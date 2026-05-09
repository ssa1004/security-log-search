package com.example.security.streaming.serde;

import com.example.security.domain.rule.Alert;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Flink job 이 발화한 Alert 을 alerts.fired Kafka topic JSON 으로 직렬화.
 *
 * <p>Spring 측 AlertFiredConsumer 가 같은 형식으로 역직렬화하므로 키 이름이 정확해야 한다.
 */
public class AlertJsonSerializer {

  private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

  public byte[] serialize(Alert alert) {
    try {
      return json.writeValueAsBytes(toMap(alert));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Alert 직렬화 실패: " + alert.alertId(), e);
    }
  }

  public String serializeToString(Alert alert) {
    try {
      return json.writeValueAsString(toMap(alert));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Alert 직렬화 실패: " + alert.alertId(), e);
    }
  }

  Map<String, Object> toMap(Alert a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("alertId", a.alertId().toString());
    m.put("tenantId", a.tenantId().value());
    m.put("ruleId", a.ruleId().toString());
    m.put("ruleName", a.ruleName());
    m.put("severity", a.severity().name());
    m.put("groupKey", a.groupKey());
    m.put("groupByField", a.groupByField());
    m.put("matchedCount", a.matchedCount());
    m.put("windowStart", a.windowStart().toString());
    m.put("windowEnd", a.windowEnd().toString());
    m.put("firedAt", a.firedAt().toString());
    m.put("status", a.status().name());
    m.put("triggeringEventIds", a.triggeringEventIds().stream().map(java.util.UUID::toString).toList());
    m.put("message", a.message());
    return m;
  }
}
