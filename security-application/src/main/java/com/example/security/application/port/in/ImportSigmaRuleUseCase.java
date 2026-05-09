package com.example.security.application.port.in;

import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.AlertRule;
import com.example.security.domain.sigma.SigmaRule;
import java.util.List;
import java.util.UUID;

/**
 * use case 9 — Sigma 룰 (YAML) 을 import → AlertRule 변환 + 저장.
 *
 * <p>Sigma 는 SigmaHQ 가 정의한 vendor 중립 SIEM 룰 표준이다. 외부 위협 인텔리전스 (예:
 * SigmaHQ public ruleset) 의 룰 묶음을 한 번에 들여와 본 시스템에 반영할 수 있다.
 */
public interface ImportSigmaRuleUseCase {

  /** 1개 또는 N 개 (multi-document YAML) 의 Sigma 룰을 import. */
  ImportResult importYaml(ImportCommand command, OperatorContext operator);

  record ImportCommand(TenantId tenantId, String yaml, boolean overwriteByTitle) {}

  /**
   * import 결과 — 변환된 AlertRule 들 + 변환 한계 (Sigma 표현 중 미지원) 목록.
   *
   * @param createdRules 새로 생성된 AlertRule 들
   * @param importedSigma import 된 Sigma 원본
   * @param mappingNotes Sigma 룰 별 변환 한계 — 매핑 안 된 표현 (timeframe / aggregation 등)
   */
  record ImportResult(
      List<AlertRule> createdRules,
      List<SigmaRule> importedSigma,
      List<MappingNote> mappingNotes) {}

  record MappingNote(UUID alertRuleId, String sigmaRuleId, List<String> unsupported) {}
}
