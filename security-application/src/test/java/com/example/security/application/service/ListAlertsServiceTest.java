package com.example.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.security.application.exception.AlertNotFoundException;
import com.example.security.application.port.in.ListAlertsUseCase.ListAlertsQuery;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.in.OperatorContext.Role;
import com.example.security.application.port.out.AlertRepository;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.Alert;
import com.example.security.domain.rule.Alert.AlertStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListAlertsServiceTest {

  @Mock AlertRepository repo;
  @Mock AuditLogPort audit;

  private ListAlertsService service;
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-09T12:00:00Z"), ZoneOffset.UTC);
  private final TenantId tenantId = TenantId.of("acme");
  private final OperatorContext op =
      new OperatorContext("alice", tenantId, "127.0.0.1", Set.of(Role.OPERATOR));

  @BeforeEach
  void setup() {
    service = new ListAlertsService(repo, audit, clock);
  }

  @Test
  void acknowledge_상태_변경() {
    var alert = sampleAlert();
    when(repo.findById(alert.alertId())).thenReturn(Optional.of(alert));
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var ack = service.acknowledge(alert.alertId(), op);

    assertThat(ack.status()).isEqualTo(AlertStatus.ACKNOWLEDGED);
  }

  @Test
  void resolve_상태_변경() {
    var alert = sampleAlert();
    when(repo.findById(alert.alertId())).thenReturn(Optional.of(alert));
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var resolved = service.resolve(alert.alertId(), op);

    assertThat(resolved.status()).isEqualTo(AlertStatus.RESOLVED);
  }

  @Test
  void false_positive_상태_변경() {
    var alert = sampleAlert();
    when(repo.findById(alert.alertId())).thenReturn(Optional.of(alert));
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var fp = service.markFalsePositive(alert.alertId(), op);

    assertThat(fp.status()).isEqualTo(AlertStatus.FALSE_POSITIVE);
  }

  @Test
  void 없는_알람_acknowledge_예외() {
    var id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.acknowledge(id, op))
        .isInstanceOf(AlertNotFoundException.class);
  }

  @Test
  void 페이지가_size_와_같으면_nextCursor_채워짐() {
    var query =
        new ListAlertsQuery(tenantId, Optional.empty(), Optional.empty(), Optional.empty(), 2, Optional.empty());
    var a1 = sampleAlert();
    var a2 = sampleAlert();
    when(repo.query(query)).thenReturn(List.of(a1, a2));

    var page = service.list(query, op);

    assertThat(page.alerts()).hasSize(2);
    assertThat(page.nextCursor()).isEqualTo(a2.alertId());
  }

  private Alert sampleAlert() {
    return new Alert(
        UUID.randomUUID(),
        tenantId,
        UUID.randomUUID(),
        "5분 안 5회 인증 실패",
        Severity.HIGH,
        "192.168.1.10",
        "source.ip",
        7,
        Instant.parse("2026-05-09T11:55:00Z"),
        Instant.parse("2026-05-09T12:00:00Z"),
        Instant.parse("2026-05-09T12:00:01Z"),
        AlertStatus.OPEN,
        List.of(UUID.randomUUID()),
        "brute-force 의심");
  }
}
