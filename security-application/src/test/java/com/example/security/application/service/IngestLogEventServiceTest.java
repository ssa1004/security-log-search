package com.example.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.security.application.exception.TenantNotFoundException;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.application.port.out.EventPublisherPort;
import com.example.security.application.port.out.IdempotencyPort;
import com.example.security.application.port.out.TenantRepository;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.RawEvent;
import com.example.security.domain.mapping.RoutingNormalizer;
import com.example.security.domain.tenant.Tenant;
import com.example.security.domain.tenant.Tenant.PiiMaskingPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestLogEventServiceTest {

  @Mock EventPublisherPort publisher;
  @Mock IdempotencyPort idempotency;
  @Mock TenantRepository tenants;
  @Mock AuditLogPort audit;

  private IngestLogEventService service;
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-09T12:00:00Z"), ZoneOffset.UTC);
  private final TenantId tenantId = TenantId.of("acme");
  private final Tenant tenant =
      new Tenant(
          tenantId,
          "Acme Corp",
          Duration.ofDays(365),
          Duration.ofDays(7),
          PiiMaskingPolicy.IP_ONLY,
          Instant.parse("2026-01-01T00:00:00Z"),
          true);

  @BeforeEach
  void setup() {
    service =
        new IngestLogEventService(
            new RoutingNormalizer(), publisher, idempotency, tenants, audit, clock);
  }

  @Test
  void 정상_ECS_event_정규화_후_publish() {
    when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(idempotency.tryClaim(eq(tenantId), eq("k1"), any())).thenReturn(true);

    Map<String, Object> payload = new HashMap<>();
    payload.put("event.category", "authentication");
    payload.put("event.outcome", "failure");
    payload.put("source.ip", "10.0.0.1");
    var raw = new RawEvent(tenantId, Instant.parse("2026-05-09T11:59:59Z"), "syslog", "ecs", payload);

    var result = service.ingest(raw, "k1");

    assertThat(result.duplicate()).isFalse();
    assertThat(result.eventId()).isNotNull();
    verify(publisher, times(1)).publish(any());
  }

  @Test
  void 같은_idempotency_key_재사용시_publish_안함() {
    when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    var existing = UUID.randomUUID();
    when(idempotency.lookup(tenantId, "k1")).thenReturn(Optional.of(existing));

    Map<String, Object> payload = new HashMap<>();
    payload.put("event.category", "process");
    var raw = new RawEvent(tenantId, Instant.parse("2026-05-09T11:59:59Z"), "edr", "ecs", payload);

    var result = service.ingest(raw, "k1");

    assertThat(result.duplicate()).isTrue();
    assertThat(result.eventId()).isEqualTo(existing);
    verify(publisher, never()).publish(any());
  }

  @Test
  void 모르는_tenant_거부() {
    when(tenants.findById(tenantId)).thenReturn(Optional.empty());

    var raw =
        new RawEvent(
            tenantId,
            Instant.parse("2026-05-09T11:59:59Z"),
            "syslog",
            "ecs",
            Map.of("event.category", "process"));

    assertThatThrownBy(() -> service.ingest(raw, null)).isInstanceOf(TenantNotFoundException.class);
    verify(publisher, never()).publish(any());
  }

  @Test
  void 비활성_tenant_거부() {
    var inactive =
        new Tenant(
            tenantId,
            "x",
            Duration.ofDays(365),
            Duration.ofDays(7),
            PiiMaskingPolicy.NONE,
            Instant.parse("2026-01-01T00:00:00Z"),
            false);
    when(tenants.findById(tenantId)).thenReturn(Optional.of(inactive));

    var raw =
        new RawEvent(
            tenantId,
            Instant.parse("2026-05-09T11:59:59Z"),
            "syslog",
            "ecs",
            Map.of("event.category", "process"));

    assertThatThrownBy(() -> service.ingest(raw, null)).isInstanceOf(TenantNotFoundException.class);
  }

  @Test
  void idempotency_key_없으면_바로_발행() {
    when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

    var raw =
        new RawEvent(
            tenantId,
            Instant.parse("2026-05-09T11:59:59Z"),
            "syslog",
            "ecs",
            Map.of("event.category", "process"));

    var result = service.ingest(raw, null);

    assertThat(result.duplicate()).isFalse();
    verify(publisher, times(1)).publish(any());
    verify(idempotency, never()).tryClaim(any(), any(), any());
  }
}
