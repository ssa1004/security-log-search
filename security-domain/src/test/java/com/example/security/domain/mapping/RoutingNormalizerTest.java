package com.example.security.domain.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.RawEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoutingNormalizerTest {

  private final RoutingNormalizer router = new RoutingNormalizer();

  @Test
  void ECS_와_OCSF_둘다_라우팅() {
    var ecs =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "syslog",
            "ecs",
            Map.of("event.category", "process"));
    var ocsf =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "edr",
            "ocsf",
            Map.of("class_uid", 3002));
    assertThat(router.normalize(ecs).eventCategory()).isEqualTo("process");
    assertThat(router.normalize(ocsf).eventCategory()).isEqualTo("authentication");
  }

  @Test
  void schema_대소문자_무관() {
    var raw =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "syslog",
            "ECS",
            Map.of("event.category", "process"));
    assertThat(router.normalize(raw).eventCategory()).isEqualTo("process");
  }

  @Test
  void 등록되지_않은_schema_거부() {
    var raw =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "vendor",
            "vendor-proprietary",
            Map.of());
    assertThatThrownBy(() -> router.normalize(raw))
        .isInstanceOf(EventNormalizer.UnsupportedSchemaException.class);
  }

  @Test
  void 추가_매퍼_등록() {
    router.register(
        "custom",
        raw ->
            new com.example.security.domain.event.LogEvent(
                java.util.UUID.randomUUID(),
                raw.tenantId(),
                raw.receivedAt(),
                raw.receivedAt(),
                "event",
                "custom",
                "info",
                "noop",
                "success",
                com.example.security.domain.common.Severity.INFO,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "custom mapper",
                Map.of()));
    var raw =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "vendor",
            "custom",
            Map.of());
    assertThat(router.normalize(raw).eventCategory()).isEqualTo("custom");
  }
}
