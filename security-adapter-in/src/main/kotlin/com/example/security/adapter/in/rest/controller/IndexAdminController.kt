package com.example.security.adapter.`in`.rest.controller

import com.example.security.adapter.`in`.security.OperatorContextResolver
import com.example.security.application.port.`in`.ManageOpenSearchIndexUseCase
import com.example.security.application.port.`in`.ManageOpenSearchIndexUseCase.RolloverResult
import com.example.security.domain.common.TenantId
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** use case 7 — admin only. /api/v1/admin/indices/{tenantId} 하위 endpoint. */
@RestController
@RequestMapping("/api/v1/admin/indices")
class IndexAdminController(
    private val useCase: ManageOpenSearchIndexUseCase,
    private val operators: OperatorContextResolver,
) {

    @PostMapping("/{tenantId}")
    fun create(@NotBlank @PathVariable tenantId: String): ResponseEntity<Void> {
        useCase.createInitialIndex(TenantId.of(tenantId), operators.currentOperator())
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/{tenantId}/rollover")
    fun rollover(@NotBlank @PathVariable tenantId: String): ResponseEntity<RolloverResult> =
        ResponseEntity.ok(useCase.triggerRollover(TenantId.of(tenantId), operators.currentOperator()))

    @PostMapping("/{tenantId}/ilm")
    fun applyIlm(@NotBlank @PathVariable tenantId: String): ResponseEntity<Void> {
        useCase.applyIlmPolicy(TenantId.of(tenantId), operators.currentOperator())
        return ResponseEntity.accepted().build()
    }
}
