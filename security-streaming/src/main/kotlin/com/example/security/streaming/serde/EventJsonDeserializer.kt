package com.example.security.streaming.serde

import com.example.security.domain.common.Severity
import com.example.security.domain.common.TenantId
import com.example.security.domain.event.LogEvent
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.time.Instant
import java.util.HashMap
import java.util.UUID

/**
 * events.normalized topic 의 JSON payload (Spring 측이 publish 한 형태) 를 LogEvent 로 역직렬화.
 *
 * Flink 의 표준 SerializationSchema 를 직접 구현하지 않고 wrapper class 로 가져가는 이유:
 * security-streaming 모듈이 Flink connector 의 SerializationSchema interface 를 compileOnly 로
 * 가지고 있어 Spring 측에서도 재사용 가능하게 분리.
 */
class EventJsonDeserializer {

    private val json: ObjectMapper = ObjectMapper().registerModule(JavaTimeModule())

    @Throws(Exception::class)
    fun deserialize(bytes: ByteArray): LogEvent {
        val m: Map<String, Any?> = json.readValue(bytes, object : TypeReference<Map<String, Any?>>() {})
        return mapToLogEvent(m)
    }

    @Throws(Exception::class)
    fun deserialize(s: String): LogEvent {
        val m: Map<String, Any?> = json.readValue(s, object : TypeReference<Map<String, Any?>>() {})
        return mapToLogEvent(m)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToLogEvent(m: Map<String, Any?>): LogEvent {
        val labelsRaw = (m.getOrDefault("labels", emptyMap<String, Any?>()) as Map<String, Any?>)
        val labels: MutableMap<String, String> = HashMap()
        labelsRaw.forEach { (k, v) -> labels[k] = v?.toString() ?: "" }
        return LogEvent(
            UUID.fromString(m["eventId"].toString()),
            TenantId.of((m["tenantId"] as Map<String, Any?>)["value"].toString()),
            Instant.parse(m["timestamp"].toString()),
            Instant.parse(m["ingestedAt"].toString()),
            asString(m["eventKind"], "event"),
            asString(m["eventCategory"], "unknown"),
            asString(m["eventType"], "info"),
            asString(m["eventAction"], null),
            asString(m["eventOutcome"], "unknown"),
            Severity.valueOf(asString(m["severity"], "INFO")!!),
            asString(m["sourceIp"], null),
            asInteger(m["sourcePort"]),
            asString(m["destinationIp"], null),
            asInteger(m["destinationPort"]),
            asString(m["userName"], null),
            asString(m["hostName"], null),
            asString(m["hostOs"], null),
            asString(m["message"], ""),
            labels,
        )
    }

    private fun asString(v: Any?, fallback: String?): String? =
        if (v == null) fallback else v.toString()

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
