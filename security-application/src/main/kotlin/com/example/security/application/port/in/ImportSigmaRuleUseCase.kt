package com.example.security.application.port.`in`

import com.example.security.domain.common.TenantId
import com.example.security.domain.rule.AlertRule
import com.example.security.domain.sigma.SigmaRule
import java.util.UUID

/**
 * use case 9 — Sigma 룰 (YAML) 을 import → AlertRule 변환 + 저장.
 *
 * Sigma 는 SigmaHQ 가 정의한 vendor 중립 SIEM 룰 표준이다. 외부 위협 인텔리전스 (예:
 * SigmaHQ public ruleset) 의 룰 묶음을 한 번에 들여와 본 시스템에 반영할 수 있다.
 */
interface ImportSigmaRuleUseCase {

    /** 1개 또는 N 개 (multi-document YAML) 의 Sigma 룰을 import. */
    fun importYaml(command: ImportCommand, operator: OperatorContext): ImportResult

    @JvmRecord
    data class ImportCommand(val tenantId: TenantId, val yaml: String, val overwriteByTitle: Boolean)

    /**
     * import 결과 — 변환된 AlertRule 들 + 변환 한계 (Sigma 표현 중 미지원) 목록.
     *
     * @property createdRules 새로 생성된 AlertRule 들
     * @property importedSigma import 된 Sigma 원본
     * @property mappingNotes Sigma 룰 별 변환 한계 — 매핑 안 된 표현 (timeframe / aggregation 등)
     */
    @JvmRecord
    data class ImportResult(
        val createdRules: List<AlertRule>,
        val importedSigma: List<SigmaRule>,
        val mappingNotes: List<MappingNote>,
    )

    @JvmRecord
    data class MappingNote(val alertRuleId: UUID, val sigmaRuleId: String, val unsupported: List<String>)
}
