package com.example.security.adapter.out.kafka;

import com.example.security.application.port.out.EventPublisherPort;
import com.example.security.domain.event.LogEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code events.normalized} Kafka topic 발행.
 *
 * <p>Kafka 측 설정 (KafkaConfig 에서):
 *
 * <ul>
 *   <li>{@code enable.idempotence=true} — 같은 메시지 중복 전송 방지
 *   <li>{@code acks=all} — 모든 ISR ack 대기 (durable)
 *   <li>{@code max.in.flight.requests.per.connection=5} — 순서 보장 + 처리량
 *   <li>partition key 는 tenantId — 같은 tenant 의 이벤트는 같은 partition (Flink 의 keyBy 효율)
 * </ul>
 */
@Component
public class EventKafkaPublisher implements EventPublisherPort {

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper json;
  private final String topic;

  public EventKafkaPublisher(
      KafkaTemplate<String, String> kafka,
      ObjectMapper json,
      @Value("${security.kafka.topics.events-normalized:events.normalized}") String topic) {
    this.kafka = kafka;
    this.json = json;
    this.topic = topic;
  }

  @Override
  public void publish(LogEvent event) {
    try {
      var payload = json.writeValueAsString(event);
      kafka.send(topic, event.tenantId().value(), payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("LogEvent 직렬화 실패: " + event.eventId(), e);
    }
  }
}
