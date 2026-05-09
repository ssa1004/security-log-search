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
  void aws_cloudtrail_라우팅() {
    java.util.Map<String, Object> userIdentity = new java.util.LinkedHashMap<>();
    userIdentity.put("type", "IAMUser");
    userIdentity.put("userName", "Alice");
    java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("eventTime", "2026-05-09T12:00:00Z");
    payload.put("eventSource", "signin.amazonaws.com");
    payload.put("eventName", "ConsoleLogin");
    payload.put("awsRegion", "us-east-1");
    payload.put("sourceIPAddress", "10.0.0.1");
    payload.put("userIdentity", userIdentity);
    payload.put("eventID", "11111111-2222-3333-4444-555555555555");
    var raw =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "aws",
            "aws-cloudtrail",
            payload);
    assertThat(router.normalize(raw).eventCategory()).isEqualTo("authentication");
  }

  @Test
  void k8s_audit_라우팅() {
    java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("auditID", "11111111-2222-3333-4444-555555555555");
    payload.put("verb", "create");
    payload.put("user", Map.of("username", "alice"));
    payload.put("sourceIPs", java.util.List.of("10.0.0.1"));
    payload.put("objectRef", Map.of("resource", "pods", "namespace", "default"));
    payload.put("responseStatus", Map.of("code", 201));
    payload.put("requestReceivedTimestamp", "2026-05-09T12:00:00Z");
    var raw =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "k8s",
            "k8s-audit",
            payload);
    assertThat(router.normalize(raw).eventCategory()).isEqualTo("configuration");
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
