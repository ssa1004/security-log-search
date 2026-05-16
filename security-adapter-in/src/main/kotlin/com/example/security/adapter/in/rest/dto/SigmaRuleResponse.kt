package com.example.security.adapter.`in`.rest.dto

import com.example.security.domain.sigma.SigmaRule
import java.time.Instant

/** 조회 응답 — Sigma 룰 메타데이터 (원본 YAML 은 별도 endpoint 로 노출). */
@JvmRecord
data class SigmaRuleResponse(
    val id: String,
    val title: String,
    val level: String?,
    val status: String?,
    val author: String?,
    val description: String?,
    val tags: List<String>,
    val references: List<String>,
    val logsourceCategory: String?,
    val logsourceProduct: String?,
    val importedAt: Instant,
) {

    companion object {
        @JvmStatic
        fun from(s: SigmaRule): SigmaRuleResponse =
            SigmaRuleResponse(
                s.id,
                s.title,
                s.level,
                s.status,
                s.author,
                s.description,
                s.tags,
                s.references,
                s.logsource["category"],
                s.logsource["product"],
                s.importedAt,
            )
    }
}
