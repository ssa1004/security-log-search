package com.example.security.domain.mapping

import com.example.security.domain.common.Severity
import com.example.security.domain.event.LogEvent
import com.example.security.domain.event.RawEvent
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * ECS (Elastic Common Schema) 8.x 매퍼.
 *
 * raw payload 가 이미 ECS 의 dotted notation (예: `"event.action"=login`) 으로 들어왔다고
 * 가정한다. 이는 클라이언트가 ECS-aware 한 (예: Filebeat, Logstash) 경우의 흐름.
 *
 * ECS spec: https://www.elastic.co/guide/en/ecs/current/index.html
 */
class EcsNormalizer : EventNormalizer {

    override fun normalize(raw: RawEvent): LogEvent {
        if (!"ecs".equals(raw.schema, ignoreCase = true)) {
            throw EventNormalizer.UnsupportedSchemaException(raw.schema)
        }

        val p = raw.payload
        val timestamp = parseTimestamp(p["@timestamp"], raw.receivedAt)
        val severityScore = asInt(p["event.severity"], 30)

        val labels = HashMap<String, String>()
        p.forEach { (k, v) ->
            // event.* / source.* / destination.* / user.* / host.* / @timestamp / message 등 ECS
            // top-level 필드는 LogEvent 에 직접 매핑되니 labels 에서 제외.
            if (k !in RESERVED) {
                labels[k] = v.toString()
            }
        }

        return LogEvent(
            eventIdOf(p),
            raw.tenantId,
            timestamp,
            raw.receivedAt,
            asString(p["event.kind"], "event"),
            asString(p["event.category"], "unknown"),
            asString(p["event.type"], "info"),
            asString(p["event.action"], null),
            asString(p["event.outcome"], "unknown"),
            Severity.fromEcsScore(severityScore),
            asString(p["source.ip"], null),
            asInteger(p["source.port"]),
            asString(p["destination.ip"], null),
            asInteger(p["destination.port"]),
            asString(p["user.name"], null),
            asString(p["host.hostname"], null),
            asString(p["host.os.name"], null),
            asString(p["message"], ""),
            java.util.Map.copyOf(labels),
        )
    }

    private companion object {
        private fun eventIdOf(p: Map<String, Any>): UUID {
            val id = p["event.id"]
            if (id != null) {
                return try {
                    UUID.fromString(id.toString())
                } catch (ignore: IllegalArgumentException) {
                    // event.id 가 UUID 가 아닌 경우 — 안정적인 deterministic UUID 로 변환.
                    UUID.nameUUIDFromBytes(id.toString().toByteArray())
                }
            }
            return UUID.randomUUID()
        }

        private fun parseTimestamp(value: Any?, fallback: Instant): Instant {
            if (value == null) return fallback
            return try {
                Instant.parse(value.toString())
            } catch (e: DateTimeParseException) {
                fallback
            }
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

        private val RESERVED: Set<String> =
            setOf(
                "@timestamp",
                "event.id",
                "event.kind",
                "event.category",
                "event.type",
                "event.action",
                "event.outcome",
                "event.severity",
                "source.ip",
                "source.port",
                "destination.ip",
                "destination.port",
                "user.name",
                "host.hostname",
                "host.os.name",
                "message",
            )
    }
}
