package com.example.security.application.service

import com.example.security.application.exception.TenantMismatchException
import com.example.security.application.port.`in`.AggregateLogStatsUseCase
import com.example.security.application.port.`in`.OperatorContext
import com.example.security.application.port.out.AuditLogPort
import com.example.security.application.port.out.EventStatsPort
import com.example.security.application.query.StatsQuery
import com.example.security.application.query.StatsResult
import com.example.security.domain.audit.AuditEntry
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service

/** use case 3 — ClickHouse 시계열 / 집계 query. */
@Service
open class AggregateLogStatsService(
    private val statsPort: EventStatsPort,
    private val audit: AuditLogPort,
    private val clock: Clock,
) : AggregateLogStatsUseCase {

    override fun aggregate(query: StatsQuery, operator: OperatorContext): StatsResult {
        if (!operator.canQueryOtherTenant() && operator.tenantId != query.tenantId) {
            throw TenantMismatchException(operator.tenantId, query.tenantId)
        }
        CrossTenantAccessAudit.recordIfCrossTenant(
            audit, clock, operator, query.tenantId, "stats", query.bucket.name,
        )
        val result = statsPort.aggregate(query)
        audit.append(
            AuditEntry(
                UUID.randomUUID(),
                query.tenantId,
                clock.instant(),
                operator.subject,
                roleString(operator),
                AuditEntry.AuditAction.STATS_QUERY,
                "stats",
                query.bucket.name,
                operator.sourceIp,
                mapOf(
                    "from" to query.from.toString(),
                    "to" to query.to.toString(),
                    "bucket" to query.bucket.name,
                    "groupBy" to query.groupByField.toString(),
                ),
            )
        )
        return result
    }

    companion object {
        private fun roleString(operator: OperatorContext): String =
            operator.roles.joinToString(",") { it.name }
    }
}
