package com.example.security.adapter.out.kafka

import com.example.security.application.port.out.EventPublisherPort
import com.example.security.domain.event.LogEvent
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * `events.normalized` Kafka topic 발행.
 *
 * Kafka 측 설정 (KafkaConfig 에서):
 *
 *  - `enable.idempotence=true` — 같은 메시지 중복 전송 방지
 *  - `acks=all` — 모든 ISR ack 대기 (durable)
 *  - `max.in.flight.requests.per.connection=5` — 순서 보장 + 처리량
 *  - partition key 는 tenantId — 같은 tenant 의 이벤트는 같은 partition (Flink 의 keyBy 효율)
 */
@Component
class EventKafkaPublisher(
    private val kafka: KafkaTemplate<String, String>,
    private val json: ObjectMapper,
    @Value("\${security.kafka.topics.events-normalized:events.normalized}")
    private val topic: String,
) : EventPublisherPort {

    override fun publish(event: LogEvent) {
        try {
            val payload = json.writeValueAsString(event)
            kafka.send(topic, event.tenantId.value, payload)
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("LogEvent 직렬화 실패: ${event.eventId}", e)
        }
    }
}
