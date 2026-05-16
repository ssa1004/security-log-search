package com.example.security.adapter.`in`.rest.controller

import com.example.security.adapter.`in`.rest.dto.AuditEntryResponse
import com.example.security.adapter.`in`.security.OperatorContextResolver
import com.example.security.application.port.`in`.QueryAuditLogUseCase
import com.example.security.application.port.`in`.QueryAuditLogUseCase.AuditQuery
import com.example.security.domain.audit.AuditEntry.AuditAction
import com.example.security.domain.common.TenantId
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.Optional
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** use case 8 — /api/v1/audit. */
@RestController
@RequestMapping("/api/v1/audit")
@Validated
class AuditController(
    private val useCase: QueryAuditLogUseCase,
    private val operators: OperatorContextResolver,
) {

    @GetMapping
    fun query(
        @NotBlank @RequestParam tenantId: String,
        @RequestParam(required = false) actor: String?,
        @RequestParam(required = false) action: AuditAction?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "100") @Min(1) @Max(AuditQuery.MAX_SIZE.toLong()) size: Int,
    ): ResponseEntity<List<AuditEntryResponse>> {
        val query = AuditQuery(
            TenantId.of(tenantId),
            Optional.ofNullable(actor),
            Optional.ofNullable(action),
            Optional.ofNullable(from),
            Optional.ofNullable(to),
            size,
        )
        return ResponseEntity.ok(
            useCase.query(query, operators.currentOperator())
                .map { AuditEntryResponse.from(it) },
        )
    }
}
