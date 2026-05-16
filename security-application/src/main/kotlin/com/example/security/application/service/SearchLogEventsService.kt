package com.example.security.application.service

import com.example.security.application.exception.TenantMismatchException
import com.example.security.application.exception.TenantNotFoundException
import com.example.security.application.port.`in`.OperatorContext
import com.example.security.application.port.`in`.SearchLogEventsUseCase
import com.example.security.application.port.out.AuditLogPort
import com.example.security.application.port.out.EventSearchPort
import com.example.security.application.port.out.TenantRepository
import com.example.security.application.query.SearchQuery
import com.example.security.application.query.SearchResult
import com.example.security.domain.audit.AuditEntry
import com.example.security.domain.common.TenantId
import com.example.security.domain.event.PiiMasker
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * use case 2 — 검색.
 *
 * 다음을 모두 강제:
 *
 *  - operator.tenantId == query.tenantId (admin 면 우회 가능)
 *  - tenantId filter 가 OpenSearch query 의 filter clause 에 항상 포함
 *  - 결과는 tenant 의 PII 정책에 따라 마스킹
 *  - 모든 검색은 audit_entries 에 기록
 */
@Service
open class SearchLogEventsService(
    private val searchPort: EventSearchPort,
    private val tenants: TenantRepository,
    private val audit: AuditLogPort,
    private val clock: Clock,
) : SearchLogEventsUseCase {

    override fun search(query: SearchQuery, operator: OperatorContext): SearchResult {
        enforceTenant(operator, query.tenantId)
        CrossTenantAccessAudit.recordIfCrossTenant(
            audit, clock, operator, query.tenantId, "search", query.luceneQueryString,
        )

        val tenant = tenants
            .findById(query.tenantId)
            .orElseThrow { TenantNotFoundException(query.tenantId) }

        val raw = searchPort.search(query)

        val maskedHits = raw.hits.map { PiiMasker.mask(it, tenant.piiPolicy) }

        val maskedResult = SearchResult(maskedHits, raw.totalHits, raw.facets, raw.nextCursor)

        auditSearch(query, operator, maskedHits.size)

        return maskedResult
    }

    private fun enforceTenant(operator: OperatorContext, requested: TenantId) {
        if (operator.canQueryOtherTenant()) return
        if (operator.tenantId != requested) {
            // 우회 시도 — audit 후 거부.
            audit.append(
                AuditEntry(
                    UUID.randomUUID(),
                    operator.tenantId,
                    clock.instant(),
                    operator.subject,
                    roleString(operator),
                    AuditEntry.AuditAction.SEARCH,
                    "tenant_mismatch",
                    requested.value,
                    operator.sourceIp,
                    mapOf("denied" to "true", "requested_tenant" to requested.value),
                )
            )
            throw TenantMismatchException(operator.tenantId, requested)
        }
    }

    private fun auditSearch(query: SearchQuery, operator: OperatorContext, returned: Int) {
        audit.append(
            AuditEntry(
                UUID.randomUUID(),
                query.tenantId,
                clock.instant(),
                operator.subject,
                roleString(operator),
                AuditEntry.AuditAction.SEARCH,
                "search",
                query.luceneQueryString,
                operator.sourceIp,
                mapOf(
                    "query" to query.luceneQueryString,
                    "filters" to query.termFilters.toString(),
                    "returned" to returned.toString(),
                ),
            )
        )
    }

    companion object {
        private fun roleString(operator: OperatorContext): String =
            operator.roles.joinToString(",") { it.name }
    }
}
