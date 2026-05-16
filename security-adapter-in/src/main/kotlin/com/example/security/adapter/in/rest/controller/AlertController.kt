package com.example.security.adapter.`in`.rest.controller

import com.example.security.adapter.`in`.rest.dto.AlertResponse
import com.example.security.adapter.`in`.security.OperatorContextResolver
import com.example.security.application.port.`in`.ListAlertsUseCase
import com.example.security.application.port.`in`.ListAlertsUseCase.ListAlertsQuery
import com.example.security.application.port.`in`.ListAlertsUseCase.Page
import com.example.security.domain.common.TenantId
import com.example.security.domain.rule.Alert.AlertStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** use case 6 — /api/v1/alerts list + acknowledge / resolve / false-positive. */
@RestController
@RequestMapping("/api/v1/alerts")
@Validated
class AlertController(
    private val useCase: ListAlertsUseCase,
    private val operators: OperatorContextResolver,
) {

    @JvmRecord
    data class AlertPageResponse(val alerts: List<AlertResponse>, val nextCursor: UUID?) {
        companion object {
            @JvmStatic
            fun from(page: Page): AlertPageResponse =
                AlertPageResponse(page.alerts.map { AlertResponse.from(it) }, page.nextCursor)
        }
    }

    @GetMapping
    fun list(
        @NotBlank @RequestParam tenantId: String,
        @RequestParam(required = false) status: AlertStatus?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "50") @Min(1) @Max(ListAlertsQuery.MAX_SIZE.toLong()) size: Int,
        @RequestParam(required = false) after: UUID?,
    ): ResponseEntity<AlertPageResponse> {
        val query = ListAlertsQuery(
            TenantId.of(tenantId),
            Optional.ofNullable(status),
            Optional.ofNullable(from),
            Optional.ofNullable(to),
            size,
            Optional.ofNullable(after),
        )
        return ResponseEntity.ok(AlertPageResponse.from(useCase.list(query, operators.currentOperator())))
    }

    @PostMapping("/{alertId}/ack")
    fun acknowledge(@PathVariable alertId: UUID): ResponseEntity<AlertResponse> =
        ResponseEntity.ok(AlertResponse.from(useCase.acknowledge(alertId, operators.currentOperator())))

    @PostMapping("/{alertId}/resolve")
    fun resolve(@PathVariable alertId: UUID): ResponseEntity<AlertResponse> =
        ResponseEntity.ok(AlertResponse.from(useCase.resolve(alertId, operators.currentOperator())))

    @PostMapping("/{alertId}/false-positive")
    fun markFalsePositive(@PathVariable alertId: UUID): ResponseEntity<AlertResponse> =
        ResponseEntity.ok(
            AlertResponse.from(useCase.markFalsePositive(alertId, operators.currentOperator())),
        )
}
