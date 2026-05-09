package com.example.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.security.application.exception.RuleNotFoundException;
import com.example.security.application.exception.TenantMismatchException;
import com.example.security.application.port.in.DefineAlertRuleUseCase.CreateRuleCommand;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.in.OperatorContext.Role;
import com.example.security.application.port.out.AlertRuleRepository;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.AlertRule;
import com.example.security.domain.rule.AlertRule.RuleType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefineAlertRuleServiceTest {

  @Mock AlertRuleRepository repo;
  @Mock AuditLogPort audit;

  private DefineAlertRuleService service;
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-09T12:00:00Z"), ZoneOffset.UTC);
  private final TenantId tenantId = TenantId.of("acme");
  private final OperatorContext admin =
      new OperatorContext("admin1", tenantId, "127.0.0.1", Set.of(Role.ADMIN));

  @BeforeEach
  void setup() {
    service = new DefineAlertRuleService(repo, audit, clock);
  }

  @Test
  void 룰_생성_후_audit_기록() {
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var cmd =
        new CreateRuleCommand(
            tenantId,
            "5분 안 5회 인증 실패",
            "brute-force 의심",
            RuleType.THRESHOLD,
            "authentication",
            "logon",
            "failure",
            "source.ip",
            5,
            Duration.ofMinutes(5),
            Severity.HIGH,
            true);

    var rule = service.create(cmd, admin);

    assertThat(rule.tenantId()).isEqualTo(tenantId);
    assertThat(rule.threshold()).isEqualTo(5);
    verify(audit, atLeastOnce()).append(any());
  }

  @Test
  void 다른_tenant_룰_생성_거부() {
    var cmd =
        new CreateRuleCommand(
            TenantId.of("other"),
            "x",
            "x",
            RuleType.THRESHOLD,
            null,
            null,
            null,
            "source.ip",
            5,
            Duration.ofMinutes(5),
            Severity.HIGH,
            true);
    assertThatThrownBy(() -> service.create(cmd, admin)).isInstanceOf(TenantMismatchException.class);
  }

  @Test
  void 없는_룰_업데이트_거부() {
    var ruleId = UUID.randomUUID();
    when(repo.findById(ruleId)).thenReturn(Optional.empty());
    var cmd =
        new CreateRuleCommand(
            tenantId,
            "x",
            "x",
            RuleType.THRESHOLD,
            null,
            null,
            null,
            "source.ip",
            5,
            Duration.ofMinutes(5),
            Severity.HIGH,
            true);
    assertThatThrownBy(() -> service.update(ruleId, cmd, admin))
        .isInstanceOf(RuleNotFoundException.class);
  }

  @Test
  void 룰_삭제도_audit() {
    var existing =
        new AlertRule(
            UUID.randomUUID(),
            tenantId,
            "x",
            "x",
            RuleType.THRESHOLD,
            null,
            null,
            null,
            "source.ip",
            5,
            Duration.ofMinutes(5),
            Severity.HIGH,
            true,
            clock.instant(),
            clock.instant());
    when(repo.findById(existing.ruleId())).thenReturn(Optional.of(existing));

    service.delete(existing.ruleId(), admin);

    verify(repo).deleteById(existing.ruleId());
    verify(audit, atLeastOnce()).append(any());
  }
}
