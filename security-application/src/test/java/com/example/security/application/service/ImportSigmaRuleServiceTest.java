package com.example.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.security.application.exception.TenantMismatchException;
import com.example.security.application.port.in.ImportSigmaRuleUseCase.ImportCommand;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.in.OperatorContext.Role;
import com.example.security.application.port.out.AlertRuleRepository;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.application.port.out.SigmaRuleRepository;
import com.example.security.application.sigma.SigmaYamlParser;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.sigma.SigmaToAlertRuleMapper;
import java.time.Clock;
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
class ImportSigmaRuleServiceTest {

  @Mock SigmaRuleRepository sigmaRepo;
  @Mock AlertRuleRepository alertRuleRepo;
  @Mock AuditLogPort audit;

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-09T12:00:00Z"), ZoneOffset.UTC);
  private final TenantId tenantId = TenantId.of("acme");
  private final OperatorContext admin =
      new OperatorContext("admin1", tenantId, "127.0.0.1", Set.of(Role.ADMIN));
  private final SigmaYamlParser parser = new SigmaYamlParser(clock);
  private final SigmaToAlertRuleMapper mapper = new SigmaToAlertRuleMapper();

  private ImportSigmaRuleService service;

  @BeforeEach
  void setup() {
    service = new ImportSigmaRuleService(sigmaRepo, alertRuleRepo, parser, mapper, audit, clock);
  }

  @Test
  void 새_Sigma_룰_import_시_alert_rule_생성_및_audit() {
    var yaml = sampleYaml("11111111-1111-1111-1111-111111111111", "First rule");
    when(sigmaRepo.findAlertRuleId(eq("11111111-1111-1111-1111-111111111111"), eq(tenantId)))
        .thenReturn(Optional.empty());
    when(alertRuleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(sigmaRepo.save(any(), eq(tenantId), any(UUID.class))).thenAnswer(inv -> inv.getArgument(0));

    var result = service.importYaml(new ImportCommand(tenantId, yaml, false), admin);

    assertThat(result.createdRules()).hasSize(1);
    assertThat(result.importedSigma()).hasSize(1);
    verify(alertRuleRepo, atLeastOnce()).save(any());
    verify(sigmaRepo, atLeastOnce()).save(any(), eq(tenantId), any(UUID.class));
    verify(audit, atLeastOnce()).append(any());
  }

  @Test
  void 이미_import_된_Sigma_는_overwrite_false_면_skip() {
    var yaml = sampleYaml("11111111-1111-1111-1111-111111111111", "Already imported");
    when(sigmaRepo.findAlertRuleId(any(), eq(tenantId)))
        .thenReturn(Optional.of(UUID.randomUUID()));

    var result = service.importYaml(new ImportCommand(tenantId, yaml, false), admin);

    assertThat(result.createdRules()).isEmpty();
    verify(alertRuleRepo, never()).save(any());
  }

  @Test
  void 이미_import_된_Sigma_는_overwrite_true_면_같은_id_로_save() {
    var existingId = UUID.randomUUID();
    var yaml = sampleYaml("11111111-1111-1111-1111-111111111111", "To overwrite");
    when(sigmaRepo.findAlertRuleId(any(), eq(tenantId))).thenReturn(Optional.of(existingId));
    when(alertRuleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(sigmaRepo.save(any(), eq(tenantId), any(UUID.class))).thenAnswer(inv -> inv.getArgument(0));

    var result = service.importYaml(new ImportCommand(tenantId, yaml, true), admin);

    assertThat(result.createdRules()).hasSize(1);
    assertThat(result.createdRules().get(0).ruleId()).isEqualTo(existingId);
  }

  @Test
  void 다른_tenant_로_import_거부() {
    var yaml = sampleYaml("11111111-1111-1111-1111-111111111111", "x");
    var cmd = new ImportCommand(TenantId.of("other"), yaml, false);
    assertThatThrownBy(() -> service.importYaml(cmd, admin))
        .isInstanceOf(TenantMismatchException.class);
  }

  @Test
  void multi_document_YAML_을_한_번에_import() {
    var yaml =
        """
        title: Rule A
        id: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
        logsource:
          category: authentication
        detection:
          selection:
            EventID: 4625
          condition: selection
        level: high
        ---
        title: Rule B
        id: bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb
        logsource:
          category: authentication
        detection:
          selection:
            EventID: 4625
          condition: selection
        level: medium
        """;
    when(sigmaRepo.findAlertRuleId(any(), any())).thenReturn(Optional.empty());
    when(alertRuleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(sigmaRepo.save(any(), eq(tenantId), any(UUID.class))).thenAnswer(inv -> inv.getArgument(0));

    var result = service.importYaml(new ImportCommand(tenantId, yaml, false), admin);

    assertThat(result.createdRules()).hasSize(2);
    assertThat(result.importedSigma()).extracting("id")
        .contains("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  }

  private String sampleYaml(String id, String title) {
    return """
        title: %s
        id: %s
        logsource:
          category: authentication
          product: windows
        detection:
          selection:
            EventID: 4625
          condition: selection
        level: high
        """
        .formatted(title, id);
  }
}
