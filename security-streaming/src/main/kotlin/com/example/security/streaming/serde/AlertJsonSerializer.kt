package com.example.security.streaming.serde

import com.example.security.domain.rule.Alert
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.util.LinkedHashMap
import java.util.UUID

/**
 * Flink job 이 발화한 Alert 을 alerts.fired Kafka topic JSON 으로 직렬화.
 *
 * Spring 측 AlertFiredConsumer 가 같은 형식으로 역직렬화하므로 키 이름이 정확해야 한다.
 */
class AlertJsonSerializer {

    private val json: ObjectMapper = ObjectMapper().registerModule(JavaTimeModule())

    fun serialize(alert: Alert): ByteArray {
        try {
            return json.writeValueAsBytes(toMap(alert))
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("Alert 직렬화 실패: ${alert.alertId}", e)
        }
    }

    fun serializeToString(alert: Alert): String {
        try {
            return json.writeValueAsString(toMap(alert))
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("Alert 직렬화 실패: ${alert.alertId}", e)
        }
    }

    internal fun toMap(a: Alert): Map<String, Any?> {
        val m: MutableMap<String, Any?> = LinkedHashMap()
        m["alertId"] = a.alertId.toString()
        m["tenantId"] = a.tenantId.value
        m["ruleId"] = a.ruleId.toString()
        m["ruleName"] = a.ruleName
        m["severity"] = a.severity.name
        m["groupKey"] = a.groupKey
        m["groupByField"] = a.groupByField
        m["matchedCount"] = a.matchedCount
        m["windowStart"] = a.windowStart.toString()
        m["windowEnd"] = a.windowEnd.toString()
        m["firedAt"] = a.firedAt.toString()
        m["status"] = a.status.name
        m["triggeringEventIds"] = a.triggeringEventIds.map(UUID::toString)
        m["message"] = a.message
        return m
    }
}
