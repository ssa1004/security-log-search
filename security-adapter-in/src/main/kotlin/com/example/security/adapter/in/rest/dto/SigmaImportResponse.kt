package com.example.security.adapter.`in`.rest.dto

import java.util.UUID

/** Sigma import 결과 — 변환된 alert_rule_id 목록 + 변환 한계 (운영자 후속 검토 항목). */
@JvmRecord
data class SigmaImportResponse(
    val importedCount: Int,
    val rules: List<RuleSummary>,
    val mappingNotes: List<MappingNote>,
) {

    @JvmRecord
    data class RuleSummary(
        val alertRuleId: UUID,
        val sigmaId: String,
        val title: String,
        val level: String,
    )

    @JvmRecord
    data class MappingNote(
        val alertRuleId: UUID,
        val sigmaId: String,
        val unsupported: List<String>,
    )
}
