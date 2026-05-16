package com.example.security.adapter.`in`.kafka

import com.example.security.adapter.`in`.metrics.SecurityLogMetrics
import com.example.security.application.port.`in`.EvaluateAlertUseCase
import com.example.security.application.port.out.IdempotencyPort
import com.example.security.domain.common.Severity
import com.example.security.domain.common.TenantId
import com.example.security.domain.rule.Alert
import com.example.security.domain.rule.Alert.AlertStatus
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

/**
 * Flink job 이 `alerts.fired` 로 보낸 메시지를 받아 use case 5 로 위임.
 *
 * 역직렬화 형식 — Flink job 의 `AlertJsonSerializer` 와 동일한 JSON 스펙 사용.
 *
 * Spring Kafka consumer 의 backpressure 튜닝은 application.yml 의
 * `spring.kafka.consumer.max-poll-records` 로 조절. (ADR-0009)
 *
 * at-least-once 중복 차단: Flink Kafka sink 의 delivery guarantee 가 `AT_LEAST_ONCE`
 * 이므로 같은 alertId 가 두 번 이상 도착할 수 있다. [IdempotencyPort] 로 alertId 를
 * tenant-scoped 키로 claim 하여 중복 처리 시 use case / notification / audit 가 두 번 실행되는
 * 것을 차단한다. 미스 시 (이미 본 alertId) 메트릭만 기록하고 정상 ack.
 */
@Component
class AlertFiredConsumer(
    private val useCase: EvaluateAlertUseCase,
    private val idempotency: IdempotencyPort,
    private val json: ObjectMapper,
    private val metrics: SecurityLogMetrics,
) {

    @KafkaListener(
        topics = ["\${security.kafka.topics.alerts-fired:alerts.fired}"],
        groupId = "\${security.kafka.consumer.alerts-fired-group:security-alerts-fired}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun onMessage(
        @Payload payload: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        try {
            val alert = parseAlert(payload)
            val key = IDEMPOTENCY_KEY_PREFIX + alert.alertId
            val claimed = idempotency.tryClaim(alert.tenantId, key, alert.alertId)
            if (!claimed) {
                // 같은 alertId 가 이전에 처리됨 — handleFired / notification / audit 두 번 실행되지
                // 않도록 silent skip. 메트릭으로만 발생량 노출.
                log.info(
                    "alerts.fired 중복 alertId 스킵: alertId={} partition={} offset={}",
                    alert.alertId, partition, offset,
                )
                metrics.recordAlertDuplicate(alert.tenantId.value)
                return
            }
            useCase.handleFired(alert)
            metrics.recordAlertFired(alert.ruleId.toString(), alert.severity.name, alert.tenantId.value)
        } catch (e: RuntimeException) {
            // log + (운영) DLQ topic 으로 publish — DLQ wiring 은 ErrorHandler 에서 한다.
            log.error("alerts.fired 처리 실패: partition={} offset={}", partition, offset, e)
            throw e
        }
    }

    // 테스트 가시성 — Java test 가 직접 호출.
    fun parseAlert(payload: String): Alert {
        val m: Map<String, Any?> = try {
            json.readValue(payload, object : TypeReference<Map<String, Any?>>() {})
        } catch (e: JsonProcessingException) {
            throw IllegalArgumentException("alerts.fired payload 파싱 실패", e)
        }
        @Suppress("UNCHECKED_CAST")
        val ids = (m.getOrDefault("triggeringEventIds", emptyList<String>()) as List<String>)
            .map { UUID.fromString(it) }
        return Alert(
            UUID.fromString(m["alertId"].toString()),
            TenantId.of(m["tenantId"].toString()),
            UUID.fromString(m["ruleId"].toString()),
            m["ruleName"].toString(),
            Severity.valueOf(m["severity"].toString()),
            m["groupKey"].toString(),
            m["groupByField"].toString(),
            (m["matchedCount"] as Number).toInt(),
            Instant.parse(m["windowStart"].toString()),
            Instant.parse(m["windowEnd"].toString()),
            Instant.parse(m["firedAt"].toString()),
            AlertStatus.valueOf(m.getOrDefault("status", "OPEN").toString()),
            ArrayList(ids),
            m.getOrDefault("message", "") as String?,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(AlertFiredConsumer::class.java)

        /** [IdempotencyPort] 키 prefix — 다른 use case 의 키와 충돌 방지. */
        const val IDEMPOTENCY_KEY_PREFIX: String = "alerts.fired:"
    }
}
