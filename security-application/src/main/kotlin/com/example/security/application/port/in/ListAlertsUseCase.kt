package com.example.security.application.port.`in`

import com.example.security.domain.common.TenantId
import com.example.security.domain.rule.Alert
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * use case 6 — 운영자가 발화된 알람 timeline 조회.
 *
 * tenantId 강제, status 필터, 기간 필터, cursor pagination.
 */
interface ListAlertsUseCase {

    fun list(query: ListAlertsQuery, operator: OperatorContext): Page

    fun acknowledge(alertId: UUID, operator: OperatorContext): Alert

    fun resolve(alertId: UUID, operator: OperatorContext): Alert

    fun markFalsePositive(alertId: UUID, operator: OperatorContext): Alert

    @JvmRecord
    data class ListAlertsQuery(
        val tenantId: TenantId,
        val status: Optional<Alert.AlertStatus>,
        val from: Optional<Instant>,
        val to: Optional<Instant>,
        val size: Int,
        val after: Optional<UUID>,
    ) {

        init {
            require(size in 1..MAX_SIZE) { "size 는 1~$MAX_SIZE: $size" }
        }

        companion object {
            /** 알람 timeline 한 페이지 상한 — UI 가 cursor 로 추가 페이지 요청. (API4 — 자원 소비 제한) */
            // const val 로 노출 — Java 의 @Max(ListAlertsQuery.MAX_SIZE) 가 컴파일 타임 상수를 요구.
            const val MAX_SIZE: Int = 500
        }
    }

    @JvmRecord
    data class Page(val alerts: List<Alert>, val nextCursor: UUID?)
}
