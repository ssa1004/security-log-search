package com.example.security.adapter.`in`.rest.controller

import com.example.security.adapter.`in`.metrics.SecurityLogMetrics
import com.example.security.adapter.`in`.rest.dto.StatsResponse
import com.example.security.adapter.`in`.security.OperatorContextResolver
import com.example.security.application.port.`in`.AggregateLogStatsUseCase
import com.example.security.application.query.StatsQuery
import com.example.security.application.query.StatsQuery.Bucket
import com.example.security.domain.common.TenantId
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Duration
import java.time.Instant
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** use case 3 — GET /api/v1/stats. */
@RestController
@RequestMapping("/api/v1/stats")
@Validated
class StatsController(
    private val useCase: AggregateLogStatsUseCase,
    private val operators: OperatorContextResolver,
    private val metrics: SecurityLogMetrics,
) {

    @GetMapping
    fun stats(
        @NotBlank @RequestParam tenantId: String,
        @RequestParam from: Instant,
        @RequestParam to: Instant,
        @RequestParam(defaultValue = "ONE_HOUR") bucket: Bucket,
        @RequestParam(required = false) groupByField: String?,
        @RequestParam(defaultValue = "10") @Min(1) @Max(1000) topN: Int,
        @RequestParam(required = false) filter: Map<String, String>?,
    ): ResponseEntity<StatsResponse> {
        val termFilters = if (filter == null) HashMap() else HashMap(filter)
        // 위에서 filter 자체를 query string parameter 로 받아 자동 매핑되었으나 reserved param 제거.
        termFilters.remove("tenantId")
        termFilters.remove("from")
        termFilters.remove("to")
        termFilters.remove("bucket")
        termFilters.remove("groupByField")
        termFilters.remove("topN")

        val query = StatsQuery(TenantId.of(tenantId), from, to, bucket, groupByField, topN, termFilters)
        val startNanos = System.nanoTime()
        val result = useCase.aggregate(query, operators.currentOperator())
        metrics.recordSearchLatency(
            tenantId, "clickhouse", Duration.ofNanos(System.nanoTime() - startNanos),
        )
        return ResponseEntity.ok(StatsResponse.from(result))
    }
}
