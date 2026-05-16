package com.example.security.adapter.`in`.rest.dto

import com.example.security.application.query.SearchResult
import com.example.security.domain.event.LogEvent
import java.time.Instant
import java.util.UUID

/**
 * POST /api/v1/search 응답 — hits / 합계 / facet / cursor.
 *
 * 도메인 LogEvent 를 그대로 노출하지 않고 EventSummary 로 평탄화한다 — wire format 분리.
 */
@JvmRecord
data class SearchResponse(
    val hits: List<EventSummary>,
    val totalHits: Long,
    val facets: Map<String, Map<String, Long>>,
    val nextCursor: String?,
) {

    @JvmRecord
    data class EventSummary(
        val eventId: UUID,
        val tenantId: String,
        val timestamp: Instant,
        val severity: String,
        val category: String?,
        val action: String?,
        val outcome: String?,
        val sourceIp: String?,
        val destinationIp: String?,
        val userName: String?,
        val hostName: String?,
        val message: String?,
    ) {
        companion object {
            @JvmStatic
            fun from(e: LogEvent): EventSummary =
                EventSummary(
                    e.eventId,
                    e.tenantId.value,
                    e.timestamp,
                    e.severity.name,
                    e.eventCategory,
                    e.eventAction,
                    e.eventOutcome,
                    e.sourceIp,
                    e.destinationIp,
                    e.userName,
                    e.hostName,
                    e.message,
                )
        }
    }

    companion object {
        @JvmStatic
        fun from(result: SearchResult): SearchResponse =
            SearchResponse(
                result.hits.map { EventSummary.from(it) },
                result.totalHits,
                result.facets,
                result.nextCursor,
            )
    }
}
