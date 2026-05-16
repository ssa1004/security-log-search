package com.example.security.adapter.`in`.rest.controller

import com.example.security.adapter.`in`.metrics.SecurityLogMetrics
import com.example.security.adapter.`in`.rest.dto.SearchRequest
import com.example.security.adapter.`in`.rest.dto.SearchResponse
import com.example.security.adapter.`in`.security.OperatorContextResolver
import com.example.security.application.port.`in`.SearchLogEventsUseCase
import com.example.security.application.query.SearchQuery
import com.example.security.domain.common.TenantId
import jakarta.validation.Valid
import java.time.Duration
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** use case 2 — POST /api/v1/search. */
@RestController
@RequestMapping("/api/v1/search")
class SearchController(
    private val useCase: SearchLogEventsUseCase,
    private val operators: OperatorContextResolver,
    private val metrics: SecurityLogMetrics,
) {

    @PostMapping
    fun search(@Valid @RequestBody req: SearchRequest): ResponseEntity<SearchResponse> {
        val query = SearchQuery(
            TenantId.of(req.tenantId),
            req.query ?: "",
            req.filters ?: emptyMap(),
            req.from,
            req.to,
            req.facets ?: emptyList(),
            req.facetSize ?: 0,
            req.size ?: 50,
            req.cursor,
        )
        val startNanos = System.nanoTime()
        val result = useCase.search(query, operators.currentOperator())
        metrics.recordSearchLatency(
            req.tenantId, "opensearch", Duration.ofNanos(System.nanoTime() - startNanos),
        )
        return ResponseEntity.ok(SearchResponse.from(result))
    }
}
