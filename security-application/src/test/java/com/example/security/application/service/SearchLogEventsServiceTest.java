package com.example.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.security.application.exception.TenantMismatchException;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.in.OperatorContext.Role;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.application.port.out.EventSearchPort;
import com.example.security.application.port.out.TenantRepository;
import com.example.security.application.query.SearchQuery;
import com.example.security.application.query.SearchResult;
import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.LogEvent;
import com.example.security.domain.tenant.Tenant;
import com.example.security.domain.tenant.Tenant.PiiMaskingPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchLogEventsServiceTest {

  @Mock EventSearchPort searchPort;
  @Mock TenantRepository tenants;
  @Mock AuditLogPort audit;

  private SearchLogEventsService service;
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-09T12:00:00Z"), ZoneOffset.UTC);
  private final TenantId tenantId = TenantId.of("acme");

  @BeforeEach
  void setup() {
    service = new SearchLogEventsService(searchPort, tenants, audit, clock);
  }

  @Test
  void 정상_검색은_PII_마스킹_적용() {
    var tenant =
        new Tenant(
            tenantId,
            "Acme",
            Duration.ofDays(365),
            Duration.ofDays(7),
            PiiMaskingPolicy.STRICT,
            Instant.parse("2026-01-01T00:00:00Z"),
            true);
    when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(searchPort.search(any())).thenReturn(new SearchResult(List.of(sampleEvent()), 1, Map.of(), null));

    var query =
        new SearchQuery(
            tenantId, "*", Map.of(), Instant.parse("2026-05-09T00:00:00Z"), Instant.parse("2026-05-09T23:59:59Z"),
            List.of(), 10, 50, null);
    var operator =
        new OperatorContext("alice", tenantId, "127.0.0.1", Set.of(Role.OPERATOR));

    var result = service.search(query, operator);

    assertThat(result.hits()).hasSize(1);
    assertThat(result.hits().get(0).sourceIp()).isEqualTo("192.168.1.***"); // 마스킹
    assertThat(result.hits().get(0).userName()).isEqualTo("a***e"); // 마스킹
    verify(audit, atLeastOnce()).append(any());
  }

  @Test
  void 다른_tenant_요청은_거부_audit_기록() {
    var operator =
        new OperatorContext("alice", TenantId.of("other"), "127.0.0.1", Set.of(Role.OPERATOR));
    var query =
        new SearchQuery(tenantId, "*", Map.of(), null, null, List.of(), 10, 50, null);

    assertThatThrownBy(() -> service.search(query, operator))
        .isInstanceOf(TenantMismatchException.class);
    verify(audit, atLeastOnce()).append(any()); // 거부 자체는 audit
    verify(searchPort, never()).search(any());
  }

  @Test
  void platform_admin_은_다른_tenant_접근_허용() {
    var tenant =
        new Tenant(
            tenantId,
            "Acme",
            Duration.ofDays(365),
            Duration.ofDays(7),
            PiiMaskingPolicy.NONE,
            Instant.parse("2026-01-01T00:00:00Z"),
            true);
    when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(searchPort.search(any())).thenReturn(SearchResult.empty());

    var operator =
        new OperatorContext(
            "platform-admin", TenantId.of("other"), "127.0.0.1", Set.of(Role.PLATFORM_ADMIN));
    var query =
        new SearchQuery(tenantId, "*", Map.of(), null, null, List.of(), 10, 50, null);

    var result = service.search(query, operator);
    assertThat(result.hits()).isEmpty();
  }

  private LogEvent sampleEvent() {
    return new LogEvent(
        UUID.randomUUID(),
        tenantId,
        Instant.parse("2026-05-09T12:00:00Z"),
        Instant.parse("2026-05-09T12:00:01Z"),
        "event",
        "authentication",
        "denied",
        "logon",
        "failure",
        Severity.MEDIUM,
        "192.168.1.10",
        12345,
        null,
        null,
        "alice",
        "host-1",
        "linux",
        "Failed login",
        Map.of());
  }
}
