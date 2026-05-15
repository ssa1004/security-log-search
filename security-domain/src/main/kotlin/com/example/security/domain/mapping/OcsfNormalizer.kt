package com.example.security.domain.mapping

import com.example.security.domain.common.Severity
import com.example.security.domain.event.LogEvent
import com.example.security.domain.event.RawEvent
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * OCSF (Open Cybersecurity Schema Framework) 매퍼.
 *
 * OCSF 는 OASIS 가 추진하는 벤더 중립 보안 로그 스키마. 본 매퍼는 OCSF 의 핵심 필드들을
 * ECS 형태의 [LogEvent] 로 매핑한다 (본 시스템은 ECS 를 1차 도메인 모델로 사용).
 *
 * OCSF 의 주요 필드와 ECS 매핑:
 * - `class_uid` (예: 3002 = Authentication) → ECS `event.category`
 * - `activity_id` (예: 1=Logon) → ECS `event.action`
 * - `status_id` (1=Success, 2=Failure) → ECS `event.outcome`
 * - `severity_id` (1~6) → ECS `event.severity` (0~100)
 * - `time` (Unix epoch ms) → ECS `@timestamp`
 * - `src_endpoint.ip` → ECS `source.ip`
 * - `actor.user.name` → ECS `user.name`
 *
 * OCSF spec: https://schema.ocsf.io/
 */
class OcsfNormalizer : EventNormalizer {

    override fun normalize(raw: RawEvent): LogEvent {
        if (!"ocsf".equals(raw.schema, ignoreCase = true)) {
            throw EventNormalizer.UnsupportedSchemaException(raw.schema)
        }

        val p = raw.payload
        val classUid = asInt(p["class_uid"], 0)
        val category = ocsfClassToEcsCategory(classUid)
        val action = ocsfActivityToEcsAction(classUid, asInt(p["activity_id"], 0))
        val outcome = ocsfStatusToEcsOutcome(asInt(p["status_id"], 0))
        val severity = ocsfSeverityToEcs(asInt(p["severity_id"], 1))

        val srcEndpoint = nestedMap(p, "src_endpoint")
        val dstEndpoint = nestedMap(p, "dst_endpoint")
        val actor = nestedMap(p, "actor")
        val actorUser = nestedMap(actor, "user")
        val device = nestedMap(p, "device")

        val labels = HashMap<String, String>()
        labels["ocsf.class_uid"] = classUid.toString()
        labels["ocsf.activity_id"] = asInt(p["activity_id"], 0).toString()

        return LogEvent(
            eventIdOf(p),
            raw.tenantId,
            parseOcsfTime(p["time"], raw.receivedAt),
            raw.receivedAt,
            "event",
            category,
            outcomeToEcsType(outcome),
            action,
            outcome,
            severity,
            asString(srcEndpoint["ip"], null),
            asInteger(srcEndpoint["port"]),
            asString(dstEndpoint["ip"], null),
            asInteger(dstEndpoint["port"]),
            asString(actorUser["name"], null),
            asString(device["hostname"], null),
            asString(nestedMap(device, "os")["name"], null),
            asString(p["message"], ""),
            java.util.Map.copyOf(labels),
        )
    }

    private companion object {
        private fun ocsfClassToEcsCategory(classUid: Int): String =
            // OCSF class_uid 의 일부 — 운영에서 빈도 높은 것만.
            when (classUid) {
                1001 -> "file" // File System Activity
                1002 -> "process" // Process Activity
                1004 -> "kernel" // Kernel Extension Activity
                2001 -> "configuration"
                3002 -> "authentication"
                3003 -> "authorization"
                4001 -> "network"
                4002 -> "network" // HTTP Activity
                4003 -> "dns"
                else -> "unknown"
            }

        private fun ocsfActivityToEcsAction(classUid: Int, activityId: Int): String {
            if (classUid == 3002) {
                return when (activityId) {
                    1 -> "logon"
                    2 -> "logoff"
                    3 -> "authentication_ticket"
                    4 -> "service_authentication"
                    else -> "authentication.$activityId"
                }
            }
            if (classUid == 4001 || classUid == 4002) {
                return when (activityId) {
                    1 -> "open"
                    2 -> "close"
                    6 -> "traffic"
                    else -> "network.$activityId"
                }
            }
            return "activity.$activityId"
        }

        private fun ocsfStatusToEcsOutcome(statusId: Int): String =
            when (statusId) {
                1 -> "success"
                2 -> "failure"
                3 -> "unknown" // Other / Unknown
                else -> "unknown"
            }

        private fun ocsfSeverityToEcs(severityId: Int): Severity =
            // OCSF severity_id: 0=Unknown, 1=Informational, 2=Low, 3=Medium, 4=High, 5=Critical, 6=Fatal.
            when (severityId) {
                0, 1 -> Severity.INFO
                2 -> Severity.LOW
                3 -> Severity.MEDIUM
                4 -> Severity.HIGH
                5, 6 -> Severity.CRITICAL
                else -> Severity.INFO
            }

        private fun outcomeToEcsType(outcome: String): String =
            when (outcome) {
                "success" -> "allowed"
                "failure" -> "denied"
                else -> "info"
            }

        private fun eventIdOf(p: Map<String, Any>): UUID {
            val raw = p["event_uid"]
            if (raw != null) {
                return try {
                    UUID.fromString(raw.toString())
                } catch (ignore: IllegalArgumentException) {
                    UUID.nameUUIDFromBytes(raw.toString().toByteArray())
                }
            }
            return UUID.randomUUID()
        }

        private fun parseOcsfTime(v: Any?, fallback: Instant): Instant {
            if (v == null) return fallback
            if (v is Number) {
                return Instant.ofEpochMilli(v.toLong())
            }
            return try {
                Instant.ofEpochMilli(v.toString().toLong())
            } catch (e: NumberFormatException) {
                try {
                    Instant.parse(v.toString())
                } catch (e2: DateTimeParseException) {
                    fallback
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun nestedMap(parent: Map<String, Any>, key: String): Map<String, Any> {
            val v = parent[key]
            if (v is Map<*, *>) return v as Map<String, Any>
            return emptyMap()
        }

        private fun asString(v: Any?, fallback: String?): String? = v?.toString() ?: fallback

        private fun asInt(v: Any?, fallback: Int): Int {
            if (v == null) return fallback
            if (v is Number) return v.toInt()
            return try {
                v.toString().toInt()
            } catch (e: NumberFormatException) {
                fallback
            }
        }

        private fun asInteger(v: Any?): Int? {
            if (v == null) return null
            if (v is Number) return v.toInt()
            return try {
                v.toString().toInt()
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}
