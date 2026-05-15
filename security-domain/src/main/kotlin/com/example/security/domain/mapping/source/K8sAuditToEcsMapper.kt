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
 * Kubernetes audit log JSON → ECS [LogEvent] 변환기.
 *
 * kube-apiserver 의 audit policy 로 생성되는 JSON 레코드 (kind=Event,
 * apiVersion=audit.k8s.io/v1) 를 SIEM 의 정규화 모델로 변환한다.
 *
 * 매핑 규칙 (K8s audit 필드 → ECS):
 * - `requestReceivedTimestamp` → `@timestamp` (없으면 `stageTimestamp`)
 * - `verb` → `event.action` (get / list / create / update / delete / watch)
 * - `user.username` → `user.name`
 * - `user.groups` → labels `user.groups`
 * - `objectRef.namespace` → labels `kubernetes.namespace`
 * - `objectRef.resource` / `subresource` / `name` → labels `kubernetes.*`
 * - `responseStatus.code` → `event.outcome` (2xx/3xx success, 4xx/5xx failure)
 * - `sourceIPs[0]` → `source.ip`
 * - `stage` → labels `kubernetes.audit.stage`
 * - `requestURI` → message 의 보강 정보
 *
 * ECS event.category 는 verb / requestURI 로 결정:
 * - `create / update / patch / delete` → `configuration`
 * - `authentication` 관련 endpoint → `authentication`
 * - 그 외 (get / list / watch) → `api`
 *
 * 참고:
 * - K8s audit log spec: https://kubernetes.io/docs/tasks/debug/debug-cluster/audit/
 * - ECS spec: https://www.elastic.co/guide/en/ecs/current/index.html
 */
class K8sAuditToEcsMapper : EventNormalizer {

    override fun normalize(raw: RawEvent): LogEvent {
        if (!SCHEMA.equals(raw.schema, ignoreCase = true)) {
            throw EventNormalizer.UnsupportedSchemaException(raw.schema)
        }

        val p = raw.payload
        val verb = asString(p["verb"], null)
        val user = nestedMap(p, "user")
        val objectRef = nestedMap(p, "objectRef")
        val responseStatus = nestedMap(p, "responseStatus")
        val responseCode = asInt(responseStatus["code"], 0)
        val outcome = outcomeOf(responseCode)
        val category = categoryOf(verb, asString(p["requestURI"], null))

        val labels = LinkedHashMap<String, String>()
        labels["kubernetes.audit.stage"] = asString(p["stage"], "ResponseComplete")!!
        putIfPresent(labels, "kubernetes.audit.level", asString(p["level"], null))
        putIfPresent(labels, "kubernetes.audit.request_uri", asString(p["requestURI"], null))
        putIfPresent(labels, "kubernetes.namespace", asString(objectRef["namespace"], null))
        putIfPresent(labels, "kubernetes.api_group", asString(objectRef["apiGroup"], null))
        putIfPresent(labels, "kubernetes.api_version", asString(objectRef["apiVersion"], null))
        putIfPresent(labels, "kubernetes.resource", asString(objectRef["resource"], null))
        putIfPresent(labels, "kubernetes.subresource", asString(objectRef["subresource"], null))
        putIfPresent(labels, "kubernetes.object_name", asString(objectRef["name"], null))
        putIfPresent(labels, "user.id", asString(user["uid"], null))
        val groups = user["groups"]
        if (groups is List<*> && groups.isNotEmpty()) {
            labels["user.groups"] = groups.joinToString(",") { it.toString() }
        }
        if (responseCode > 0) {
            labels["http.response.status_code"] = responseCode.toString()
        }

        val sourceIp = firstSourceIp(p["sourceIPs"])
        val hostName = asString(p["kind"], "Event")
        val severity = severityOf(outcome, verb, responseCode)

        return LogEvent(
            eventIdOf(p),
            raw.tenantId,
            parseTimestamp(p["requestReceivedTimestamp"], p["stageTimestamp"], raw.receivedAt),
            raw.receivedAt,
            "event",
            category,
            outcomeToType(outcome),
            verb,
            outcome,
            severity,
            sourceIp,
            null,
            null,
            null,
            asString(user["username"], null),
            hostName,
            null,
            messageOf(
                verb,
                asString(user["username"], null),
                labels["kubernetes.resource"],
                labels["kubernetes.namespace"],
                responseCode,
            ),
            java.util.Map.copyOf(labels),
        )
    }

    companion object {
        /** RawEvent.schema 가 이 값일 때만 본 매퍼가 처리한다. */
        const val SCHEMA: String = "k8s-audit"

        private fun categoryOf(verb: String?, requestUri: String?): String {
            if (requestUri != null) {
                val u = requestUri.lowercase(Locale.ROOT)
                if (u.contains("/authentication.k8s.io/") ||
                    u.contains("tokenreviews") ||
                    u.contains("subjectaccessreviews")
                ) {
                    return "authentication"
                }
            }
            if (verb == null) return "unknown"
            return when (verb.lowercase(Locale.ROOT)) {
                "create", "update", "patch", "delete", "deletecollection" -> "configuration"
                "get", "list", "watch" -> "api"
                else -> "api"
            }
        }

        private fun outcomeOf(code: Int): String {
            if (code == 0) return "unknown"
            if (code in 200..399) return "success"
            return "failure"
        }

        private fun outcomeToType(outcome: String): String =
            when (outcome) {
                "success" -> "allowed"
                "failure" -> "denied"
                else -> "info"
            }

        private fun severityOf(outcome: String, verb: String?, code: Int): Severity {
            if ("failure" == outcome) {
                if (code == 401 || code == 403) return Severity.HIGH // 인증 / 인가 실패
                if (code >= 500) return Severity.MEDIUM
                return Severity.LOW
            }
            if (verb != null) {
                val v = verb.lowercase(Locale.ROOT)
                if ("delete" == v || "deletecollection" == v) return Severity.MEDIUM
                if ("create" == v || "update" == v || "patch" == v) return Severity.LOW
            }
            return Severity.INFO
        }

        private fun messageOf(
            verb: String?,
            user: String?,
            resource: String?,
            namespace: String?,
            code: Int,
        ): String {
            val sb = StringBuilder()
            sb.append("k8s-audit ")
            sb.append(verb ?: "?")
            if (resource != null) {
                sb.append(' ').append(resource)
            }
            if (namespace != null) {
                sb.append(" in ").append(namespace)
            }
            sb.append(" by ").append(user ?: "(unknown)")
            if (code > 0) {
                sb.append(" → ").append(code)
            }
            return sb.toString()
        }

        private fun eventIdOf(p: Map<String, Any>): UUID {
            val id = p["auditID"]
            if (id != null) {
                return try {
                    UUID.fromString(id.toString())
                } catch (ignore: IllegalArgumentException) {
                    UUID.nameUUIDFromBytes(id.toString().toByteArray())
                }
            }
            return UUID.randomUUID()
        }

        private fun parseTimestamp(primary: Any?, secondary: Any?, fallback: Instant): Instant {
            val picked = primary ?: secondary
            if (picked == null) return fallback
            return try {
                Instant.parse(picked.toString())
            } catch (e: DateTimeParseException) {
                fallback
            }
        }

        private fun firstSourceIp(value: Any?): String? {
            if (value is List<*> && value.isNotEmpty()) {
                val first = value[0]
                return first?.toString()
            }
            return null
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

        private fun putIfPresent(labels: MutableMap<String, String>, key: String, value: String?) {
            if (value != null && value.isNotBlank()) {
                labels[key] = value
            }
        }
    }
}
