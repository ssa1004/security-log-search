package com.example.security.application.query;

import com.example.security.domain.common.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 검색 query — REST API 의 검색 요청을 application layer 가 이해하는 형태로 변환한 결과.
 *
 * <p>SearchService 가 OpenSearch 로 호출하기 전에 tenantId 강제 주입을 검증한다.
 *
 * @param tenantId 필수 — 우회 금지
 * @param luceneQueryString Lucene query string. 예: "event.action:logon AND user.name:alice".
 *     비어있으면 match_all.
 * @param termFilters 정확한 매치 필터. 예: event.outcome=failure
 * @param from / to 시간 범위
 * @param facets 결과에서 수집할 terms aggregation 필드 (예: ["source.ip", "user.name"])
 * @param facetSize 각 facet 의 top-N 크기
 * @param size 페이지 크기 (max 1000)
 * @param cursor cursor pagination 의 search_after 토큰 (null 이면 첫 페이지)
 */
public record SearchQuery(
    TenantId tenantId,
    String luceneQueryString,
    Map<String, String> termFilters,
    Instant from,
    Instant to,
    List<String> facets,
    int facetSize,
    int size,
    String cursor) {

  public SearchQuery {
    Objects.requireNonNull(tenantId, "tenantId 는 필수 — 우회 불가");
    luceneQueryString = luceneQueryString == null ? "" : luceneQueryString;
    termFilters = termFilters == null ? Map.of() : Map.copyOf(termFilters);
    facets = facets == null ? List.of() : List.copyOf(facets);
    if (size < 1 || size > 1000) {
      throw new IllegalArgumentException("size 는 1~1000: " + size);
    }
    if (facetSize < 0 || facetSize > 100) {
      throw new IllegalArgumentException("facetSize 는 0~100: " + facetSize);
    }
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("from 이 to 보다 이후: " + from + " > " + to);
    }
  }
}
