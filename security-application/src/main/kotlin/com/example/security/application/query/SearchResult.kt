package com.example.security.application.query

import com.example.security.domain.event.LogEvent

/**
 * 검색 결과.
 *
 * @property hits 페이지 크기만큼의 LogEvent
 * @property totalHits 전체 매치 카운트 (track_total_hits=true 인 경우만 정확, 그렇지 않으면 lower bound)
 * @property facets 요청한 facet 별 top-N — 키: facet 필드, 값: term -> count map
 * @property nextCursor 다음 페이지의 search_after 토큰 (없으면 null)
 */
@JvmRecord
data class SearchResult(
    val hits: List<LogEvent>,
    val totalHits: Long,
    val facets: Map<String, Map<String, Long>>,
    val nextCursor: String?,
) {

    companion object {
        @JvmStatic
        fun empty(): SearchResult = SearchResult(emptyList(), 0, emptyMap(), null)
    }
}
