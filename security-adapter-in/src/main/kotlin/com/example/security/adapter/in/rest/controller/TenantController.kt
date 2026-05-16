package com.example.security.adapter.`in`.rest.controller

import com.example.security.adapter.`in`.rest.dto.TenantOnboardRequest
import com.example.security.adapter.`in`.rest.dto.TenantResponse
import com.example.security.adapter.`in`.security.OperatorContextResolver
import com.example.security.application.port.`in`.OnboardTenantUseCase
import com.example.security.application.port.`in`.OnboardTenantUseCase.OnboardCommand
import com.example.security.domain.common.TenantId
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** use case 9 — /api/v1/tenants. */
@RestController
@RequestMapping("/api/v1/tenants")
class TenantController(
    private val useCase: OnboardTenantUseCase,
    private val operators: OperatorContextResolver,
) {

    @PostMapping
    fun onboard(@Valid @RequestBody req: TenantOnboardRequest): ResponseEntity<TenantResponse> {
        val cmd = OnboardCommand(
            TenantId.of(req.tenantId),
            req.displayName,
            req.retention,
            req.hotRetention,
            req.piiPolicy,
        )
        val tenant = useCase.onboard(cmd, operators.currentOperator())
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(tenant))
    }

    @DeleteMapping("/{tenantId}")
    fun deactivate(@NotBlank @PathVariable tenantId: String): ResponseEntity<Void> {
        useCase.deactivate(TenantId.of(tenantId), operators.currentOperator())
        return ResponseEntity.noContent().build()
    }
}
