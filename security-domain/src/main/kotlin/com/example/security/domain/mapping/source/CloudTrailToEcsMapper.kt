package com.example.security.domain.mapping.source

import com.example.security.domain.common.Severity
import com.example.security.domain.event.LogEvent
import com.example.security.domain.event.RawEvent
import com.example.security.domain.mapping.EventNormalizer
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID

/**
 * AWS CloudTrail 레코드 → ECS [LogEvent] 변환기.
 *
 * CloudTrail 은 AWS 계정 활동 (API call / management event / data event) 을 JSON 으로
 * 기록한다. SIEM 에서는 이 raw 레코드를 그대로 받아 ECS 로 정규화해야 검색 / 룰 평가가 가능하다.
 *
 * 매핑 규칙 (CloudTrail 필드 → ECS):
 * - `eventTime` → `@timestamp`
 * - `eventName` → `event.action` (예: ConsoleLogin / AssumeRole)
 * - `eventSource` → `event.provider` (예: signin.amazonaws.com)
 * - `userIdentity.arn` → `user.id`
 * - `userIdentity.userName` → `user.name` (없으면 `sessionContext` 의 principalId 로 fallback)
 * - `sourceIPAddress` → `source.ip`
 * - `awsRegion` → `cloud.region` (labels)
 * - `requestParameters` → `event.original` (labels 에 JSON 문자열 보존)
 * - `errorCode` 존재 → `event.outcome=failure`, 없으면 `success`
 *
 * ECS event.category 는 CloudTrail 대부분이 IAM / API 콜 → `authentication` (ConsoleLogin,
 * AssumeRole 등) 또는 `iam` / `configuration` 으로 분기한다. 본 매퍼는 단순화를 위해 이벤트
 * 이름의 prefix 로 분류한다 (정확한 카테고리는 후속 enrichment 단계에서 보강).
 *
 * 참고:
 * - CloudTrail record reference: AWS docs — User Guide / cloudtrail-event-reference-record-contents
 * - ECS spec: https://www.elastic.co/guide/en/ecs/current/index.html
 */
class CloudTrailToEcsMapper : EventNormalizer {

    override fun normalize(raw: RawEvent): LogEvent {
        if (!SCHEMA.equals(raw.schema, ignoreCase = true)) {
            throw EventNormalizer.UnsupportedSchemaException(raw.schema)
        }

        val p = raw.payload
        val eventName = asString(p["eventName"], null)
        val eventSource = asString(p["eventSource"], null)
        val errorCode = asString(p["errorCode"], null)
        val outcome = if (errorCode == null) "success" else "failure"
        val category = categoryOf(eventName, eventSource)

        val userIdentity = nestedMap(p, "userIdentity")
        val sessionContext = nestedMap(userIdentity, "sessionContext")
        val sessionIssuer = nestedMap(sessionContext, "sessionIssuer")
        val userArn = asString(userIdentity["arn"], null)
        val userName = userNameOf(userIdentity, sessionIssuer)

        val labels = LinkedHashMap<String, String>()
        putIfPresent(labels, "event.provider", eventSource)
        putIfPresent(labels, "user.id", userArn)
        putIfPresent(labels, "cloud.provider", "aws")
        putIfPresent(labels, "cloud.region", asString(p["awsRegion"], null))
        putIfPresent(labels, "cloud.account.id", asString(p["recipientAccountId"], null))
        putIfPresent(labels, "aws.cloudtrail.event_type", asString(p["eventType"], null))
        putIfPresent(
            labels,
            "aws.cloudtrail.user_identity.type",
            asString(userIdentity["type"], null),
        )
        putIfPresent(
            labels,
            "aws.cloudtrail.user_identity.principal_id",
            asString(userIdentity["principalId"], null),
        )
        if (errorCode != null) {
            putIfPresent(labels, "error.code", errorCode)
            putIfPresent(labels, "error.message", asString(p["errorMessage"], null))
        }
        val requestParams = p["requestParameters"]
        if (requestParams != null) {
            labels["event.original.request_parameters"] = requestParams.toString()
        }
        val responseElements = p["responseElements"]
        if (responseElements != null) {
            labels["event.original.response_elements"] = responseElements.toString()
        }

        val severity = severityOf(outcome, category)
        val sourceIp = asString(p["sourceIPAddress"], null)
        val hostName = asString(p["recipientAccountId"], null)

        return LogEvent(
            eventIdOf(p),
            raw.tenantId,
            parseEventTime(p["eventTime"], raw.receivedAt),
            raw.receivedAt,
            "event",
            category,
            if (outcome == "success") "allowed" else "denied",
            eventName,
            outcome,
            severity,
            sourceIp,
            null,
            null,
            null,
            userName,
            hostName,
            null,
            messageOf(eventName, userName, outcome),
            java.util.Map.copyOf(labels),
        )
    }

    companion object {
        /** RawEvent.schema 가 이 값일 때만 본 매퍼가 처리한다. */
        const val SCHEMA: String = "aws-cloudtrail"

        private fun categoryOf(eventName: String?, eventSource: String?): String {
            if (eventName == null) return "unknown"
            val n = eventName.lowercase(Locale.ROOT)
            if (n.startsWith("consolelogin") ||
                n.contains("login") ||
                n.contains("assumerole") ||
                n.contains("getsessiontoken")
            ) {
                return "authentication"
            }
            if (n.startsWith("create") ||
                n.startsWith("update") ||
                n.startsWith("put") ||
                n.startsWith("delete") ||
                n.startsWith("modify")
            ) {
                return "configuration"
            }
            if (eventSource != null && eventSource.contains("iam.amazonaws.com")) {
                return "iam"
            }
            return "api"
        }

        private fun userNameOf(
            userIdentity: Map<String, Any>,
            sessionIssuer: Map<String, Any>,
        ): String? {
            val direct = asString(userIdentity["userName"], null)
            if (direct != null) return direct
            val issuerName = asString(sessionIssuer["userName"], null)
            if (issuerName != null) return issuerName
            return asString(userIdentity["principalId"], null)
        }

        private fun severityOf(outcome: String, category: String): Severity {
            if ("failure" == outcome && "authentication" == category) {
                return Severity.HIGH
            }
            if ("failure" == outcome) {
                return Severity.MEDIUM
            }
            if ("authentication" == category) {
                return Severity.LOW
            }
            return Severity.INFO
        }

        private fun messageOf(eventName: String?, userName: String?, outcome: String): String {
            if (eventName == null) return ""
            val who = userName ?: "(unknown)"
            return "CloudTrail $eventName by $who — $outcome"
        }

        private fun eventIdOf(p: Map<String, Any>): UUID {
            val id = p["eventID"]
            if (id != null) {
                return try {
                    UUID.fromString(id.toString())
                } catch (ignore: IllegalArgumentException) {
                    UUID.nameUUIDFromBytes(id.toString().toByteArray())
                }
            }
            return UUID.randomUUID()
        }

        private fun parseEventTime(value: Any?, fallback: Instant): Instant {
            if (value == null) return fallback
            return try {
                Instant.parse(value.toString())
            } catch (e: DateTimeParseException) {
                fallback
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun nestedMap(parent: Map<String, Any>, key: String): Map<String, Any> {
            val v = parent[key]
            if (v is Map<*, *>) return v as Map<String, Any>
            return emptyMap()
        }

        private fun asString(v: Any?, fallback: String?): String? = v?.toString() ?: fallback

        private fun putIfPresent(labels: MutableMap<String, String>, key: String, value: String?) {
            if (value != null && value.isNotBlank()) {
                labels[key] = value
            }
        }

        /** 명시적으로 mutable map 이 필요할 때 사용 — 본 클래스는 주로 LinkedHashMap. */
        @Suppress("unused")
        private fun mutable(base: Map<String, String>): HashMap<String, String> = HashMap(base)
    }
}
