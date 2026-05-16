package com.example.security.application.query

import com.example.security.domain.common.TenantId
import java.time.Instant

/**
 * 검색 query — REST API 의 검색 요청을 application layer 가 이해하는 형태로 변환한 결과.
 *
 * SearchService 가 OpenSearch 로 호출하기 전에 tenantId 강제 주입을 검증한다.
 *
 * @property tenantId 필수 — 우회 금지
 * @property luceneQueryString Lucene query string. 예: "event.action:logon AND user.name:alice".
 *     비어있으면 match_all.
 * @property termFilters 정확한 매치 필터. 예: event.outcome=failure
 * @property from / to 시간 범위
 * @property facets 결과에서 수집할 terms aggregation 필드 (예: ["source.ip", "user.name"])
 * @property facetSize 각 facet 의 top-N 크기
 * @property size 페이지 크기 (max 1000)
 * @property cursor cursor pagination 의 search_after 토큰 (null 이면 첫 페이지)
 */
@JvmRecord
data class SearchQuery(
    val tenantId: TenantId,
    val luceneQueryString: String,
    val termFilters: Map<String, String>,
    val from: Instant?,
    val to: Instant?,
    val facets: List<String>,
    val facetSize: Int,
    val size: Int,
    val cursor: String?,
) {

    init {
        require(size in 1..1000) { "size 는 1~1000: $size" }
        require(facetSize in 0..100) { "facetSize 는 0~100: $facetSize" }
        require(from == null || to == null || !from.isAfter(to)) {
            "from 이 to 보다 이후: $from > $to"
        }
    }
}
