package com.example.security.streaming.serde;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.LogEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * events.normalized topic 의 JSON payload (Spring 측이 publish 한 형태) 를 LogEvent 로 역직렬화.
 *
 * <p>Flink 의 표준 SerializationSchema 를 직접 구현하지 않고 wrapper class 로 가져가는 이유:
 * security-streaming 모듈이 Flink connector 의 SerializationSchema interface 를 compileOnly 로
 * 가지고 있어 Spring 측에서도 재사용 가능하게 분리.
 */
public class EventJsonDeserializer {

  private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

  public LogEvent deserialize(byte[] bytes) throws Exception {
    Map<String, Object> m = json.readValue(bytes, new TypeReference<Map<String, Object>>() {});
    return mapToLogEvent(m);
  }

  public LogEvent deserialize(String s) throws Exception {
    Map<String, Object> m = json.readValue(s, new TypeReference<Map<String, Object>>() {});
    return mapToLogEvent(m);
  }

  @SuppressWarnings("unchecked")
  private LogEvent mapToLogEvent(Map<String, Object> m) {
    var labelsRaw = (Map<String, Object>) m.getOrDefault("labels", Map.of());
    Map<String, String> labels = new HashMap<>();
    labelsRaw.forEach((k, v) -> labels.put(k, v == null ? "" : v.toString()));
    return new LogEvent(
        UUID.fromString(m.get("eventId").toString()),
        TenantId.of(((Map<String, Object>) m.get("tenantId")).get("value").toString()),
        Instant.parse(m.get("timestamp").toString()),
        Instant.parse(m.get("ingestedAt").toString()),
        asString(m.get("eventKind"), "event"),
        asString(m.get("eventCategory"), "unknown"),
        asString(m.get("eventType"), "info"),
        asString(m.get("eventAction"), null),
        asString(m.get("eventOutcome"), "unknown"),
        Severity.valueOf(asString(m.get("severity"), "INFO")),
        asString(m.get("sourceIp"), null),
        asInteger(m.get("sourcePort")),
        asString(m.get("destinationIp"), null),
        asInteger(m.get("destinationPort")),
        asString(m.get("userName"), null),
        asString(m.get("hostName"), null),
        asString(m.get("hostOs"), null),
        asString(m.get("message"), ""),
        labels);
  }

  private static String asString(Object v, String fallback) {
    return v == null ? fallback : v.toString();
  }

  private static Integer asInteger(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(v.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
