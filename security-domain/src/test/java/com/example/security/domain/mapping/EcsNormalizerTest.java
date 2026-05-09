package com.example.security.domain.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.RawEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EcsNormalizerTest {

  private final EcsNormalizer normalizer = new EcsNormalizer();

  @Test
  void ECS_payload_정규화() {
    java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("@timestamp", "2026-05-09T11:59:59Z");
    payload.put("event.id", "550e8400-e29b-41d4-a716-446655440000");
    payload.put("event.kind", "event");
    payload.put("event.category", "authentication");
    payload.put("event.action", "logon");
    payload.put("event.outcome", "failure");
    payload.put("event.severity", 70);
    payload.put("source.ip", "192.168.1.10");
    payload.put("source.port", 54321);
    payload.put("user.name", "alice");
    payload.put("host.hostname", "web-1");
    payload.put("host.os.name", "linux");
    payload.put("message", "Failed password for alice from 192.168.1.10");
    var raw =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "firewall",
            "ecs",
            payload);

    var event = normalizer.normalize(raw);

    assertThat(event.eventCategory()).isEqualTo("authentication");
    assertThat(event.eventOutcome()).isEqualTo("failure");
    assertThat(event.severity()).isEqualTo(Severity.HIGH); // 70 → HIGH
    assertThat(event.sourceIp()).isEqualTo("192.168.1.10");
    assertThat(event.sourcePort()).isEqualTo(54321);
    assertThat(event.userName()).isEqualTo("alice");
    assertThat(event.tenantId().value()).isEqualTo("acme");
    assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-05-09T11:59:59Z"));
  }

  @Test
  void event_id_누락시_새로_생성() {
    var raw =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "syslog",
            "ecs",
            Map.of("event.category", "process", "event.action", "exec"));

    var event = normalizer.normalize(raw);
    assertThat(event.eventId()).isNotNull();
    assertThat(event.eventCategory()).isEqualTo("process");
  }

  @Test
  void event_id_가_UUID_가_아니면_deterministic_변환() {
    var raw =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "vendor-x",
            "ecs",
            Map.of("event.id", "non-uuid-id-12345", "event.category", "file"));
    var first = normalizer.normalize(raw);
    var second = normalizer.normalize(raw);
    assertThat(first.eventId()).isEqualTo(second.eventId()); // deterministic
  }

  @Test
  void 라벨에는_매핑되지_않은_필드만() {
    java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("event.category", "process");
    payload.put("process.name", "bash");
    payload.put("process.pid", 1234);
    payload.put("vendor.detection_id", "abc-123");
    var raw =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "edr",
            "ecs",
            payload);
    var event = normalizer.normalize(raw);
    assertThat(event.labels())
        .containsEntry("process.name", "bash")
        .containsEntry("process.pid", "1234")
        .containsEntry("vendor.detection_id", "abc-123");
  }

  @Test
  void 잘못된_schema_거부() {
    var raw =
        new RawEvent(TenantId.of("acme"), Instant.now(), "syslog", "ocsf", Map.of());
    assertThatThrownBy(() -> normalizer.normalize(raw))
        .isInstanceOf(EventNormalizer.UnsupportedSchemaException.class);
  }
}
