package com.example.security.adapter.in.kafka;

import com.example.security.adapter.in.metrics.SecurityLogMetrics;
import com.example.security.application.port.in.EvaluateAlertUseCase;
import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.Alert;
import com.example.security.domain.rule.Alert.AlertStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Flink job 이 {@code alerts.fired} 로 보낸 메시지를 받아 use case 5 로 위임.
 *
 * <p>역직렬화 형식 — Flink job 의 {@code AlertJsonSerializer} 와 동일한 JSON 스펙 사용.
 *
 * <p>Spring Kafka consumer 의 backpressure 튜닝은 application.yml 의
 * {@code spring.kafka.consumer.max-poll-records} 로 조절. (ADR-0009)
 */
@Component
public class AlertFiredConsumer {

  private static final Logger log = LoggerFactory.getLogger(AlertFiredConsumer.class);

  private final EvaluateAlertUseCase useCase;
  private final ObjectMapper json;
  private final SecurityLogMetrics metrics;

  public AlertFiredConsumer(EvaluateAlertUseCase useCase, ObjectMapper json, SecurityLogMetrics metrics) {
    this.useCase = useCase;
    this.json = json;
    this.metrics = metrics;
  }

  @KafkaListener(
      topics = "${security.kafka.topics.alerts-fired:alerts.fired}",
      groupId = "${security.kafka.consumer.alerts-fired-group:security-alerts-fired}",
      containerFactory = "kafkaListenerContainerFactory")
  public void onMessage(
      @Payload String payload,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.OFFSET) long offset) {
    try {
      var alert = parseAlert(payload);
      useCase.handleFired(alert);
      metrics.recordAlertFired(alert.ruleId().toString(), alert.severity().name(), alert.tenantId().value());
    } catch (RuntimeException e) {
      // log + (운영) DLQ topic 으로 publish — DLQ wiring 은 ErrorHandler 에서 한다.
      log.error("alerts.fired 처리 실패: partition={} offset={}", partition, offset, e);
      throw e;
    }
  }

  Alert parseAlert(String payload) {
    Map<String, Object> m;
    try {
      m = json.readValue(payload, new TypeReference<Map<String, Object>>() {});
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("alerts.fired payload 파싱 실패", e);
    }
    @SuppressWarnings("unchecked")
    var ids =
        ((List<String>) m.getOrDefault("triggeringEventIds", List.of()))
            .stream().map(UUID::fromString).toList();
    return new Alert(
        UUID.fromString(m.get("alertId").toString()),
        TenantId.of(m.get("tenantId").toString()),
        UUID.fromString(m.get("ruleId").toString()),
        m.get("ruleName").toString(),
        Severity.valueOf(m.get("severity").toString()),
        m.get("groupKey").toString(),
        m.get("groupByField").toString(),
        ((Number) m.get("matchedCount")).intValue(),
        Instant.parse(m.get("windowStart").toString()),
        Instant.parse(m.get("windowEnd").toString()),
        Instant.parse(m.get("firedAt").toString()),
        AlertStatus.valueOf(m.getOrDefault("status", "OPEN").toString()),
        new ArrayList<>(ids),
        (String) m.getOrDefault("message", ""));
  }
}
