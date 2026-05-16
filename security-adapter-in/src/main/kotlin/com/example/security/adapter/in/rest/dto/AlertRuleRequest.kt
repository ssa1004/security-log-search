package com.example.security.adapter.`in`.rest.dto

import com.example.security.domain.common.Severity
import com.example.security.domain.rule.AlertRule.RuleType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Duration

/**
 * POST /api/v1/alert-rules — 알람 룰 정의 요청.
 *
 * type 별로 채워야 하는 filter 필드가 달라진다 (controller / use case 에서 검증).
 */
@JvmRecord
data class AlertRuleRequest(
    @field:NotBlank val tenantId: String,
    @field:NotBlank val name: String,
    val description: String?,
    @field:NotNull val type: RuleType,
    val filterCategory: String?,
    val filterAction: String?,
    val filterOutcome: String?,
    @field:NotBlank val groupByField: String,
    @field:Min(1) val threshold: Int,
    @field:NotNull val window: Duration,
    @field:NotNull val severity: Severity,
    val enabled: Boolean,
)
