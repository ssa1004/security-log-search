package com.example.security.domain.mapping;

import com.example.security.domain.common.Severity;
import com.example.security.domain.event.LogEvent;
import com.example.security.domain.event.RawEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ECS (Elastic Common Schema) 8.x 매퍼.
 *
 * <p>raw payload 가 이미 ECS 의 dotted notation (예: {@code "event.action"=login}) 으로
 * 들어왔다고 가정한다. 이는 클라이언트가 ECS-aware 한 (예: Filebeat, Logstash) 경우의 흐름.
 *
 * <p>ECS spec: <a href="https://www.elastic.co/guide/en/ecs/current/index.html">elastic.co/ecs</a>.
 */
public class EcsNormalizer implements EventNormalizer {

  @Override
  public LogEvent normalize(RawEvent raw) {
    if (!"ecs".equalsIgnoreCase(raw.schema())) {
      throw new UnsupportedSchemaException(raw.schema());
    }

    var p = raw.payload();
    var timestamp = parseTimestamp(p.get("@timestamp"), raw.receivedAt());
    var severityScore = asInt(p.get("event.severity"), 30);

    var labels = new HashMap<String, String>();
    p.forEach(
        (k, v) -> {
          // event.* / source.* / destination.* / user.* / host.* / @timestamp / message 등 ECS
          // top-level 필드는 LogEvent 에 직접 매핑되니 labels 에서 제외.
          if (!RESERVED.contains(k) && v != null) {
            labels.put(k, v.toString());
          }
        });

    return new LogEvent(
        eventIdOf(p),
        raw.tenantId(),
        timestamp,
        raw.receivedAt(),
        asString(p.get("event.kind"), "event"),
        asString(p.get("event.category"), "unknown"),
        asString(p.get("event.type"), "info"),
        asString(p.get("event.action"), null),
        asString(p.get("event.outcome"), "unknown"),
        Severity.fromEcsScore(severityScore),
        asString(p.get("source.ip"), null),
        asInteger(p.get("source.port")),
        asString(p.get("destination.ip"), null),
        asInteger(p.get("destination.port")),
        asString(p.get("user.name"), null),
        asString(p.get("host.hostname"), null),
        asString(p.get("host.os.name"), null),
        asString(p.get("message"), ""),
        Map.copyOf(labels));
  }

  private static UUID eventIdOf(Map<String, Object> p) {
    var id = p.get("event.id");
    if (id != null) {
      try {
        return UUID.fromString(id.toString());
      } catch (IllegalArgumentException ignore) {
        // event.id 가 UUID 가 아닌 경우 — 안정적인 deterministic UUID 로 변환.
        return UUID.nameUUIDFromBytes(id.toString().getBytes());
      }
    }
    return UUID.randomUUID();
  }

  private static Instant parseTimestamp(Object value, Instant fallback) {
    if (value == null) return fallback;
    try {
      return Instant.parse(value.toString());
    } catch (java.time.format.DateTimeParseException e) {
      return fallback;
    }
  }

  private static String asString(Object v, String fallback) {
    return v == null ? fallback : v.toString();
  }

  private static int asInt(Object v, int fallback) {
    if (v == null) return fallback;
    if (v instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(v.toString());
    } catch (NumberFormatException e) {
      return fallback;
    }
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

  private static final java.util.Set<String> RESERVED =
      java.util.Set.of(
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
          "message");
}
