package com.example.security.application.query;

import com.example.security.domain.event.LogEvent;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 검색 결과.
 *
 * @param hits 페이지 크기만큼의 LogEvent
 * @param totalHits 전체 매치 카운트 (track_total_hits=true 인 경우만 정확, 그렇지 않으면 lower bound)
 * @param facets 요청한 facet 별 top-N — 키: facet 필드, 값: term -> count map
 * @param nextCursor 다음 페이지의 search_after 토큰 (없으면 null)
 */
public record SearchResult(
    List<LogEvent> hits,
    long totalHits,
    Map<String, Map<String, Long>> facets,
    String nextCursor) {

  public SearchResult {
    Objects.requireNonNull(hits);
    hits = List.copyOf(hits);
    facets = facets == null ? Map.of() : Map.copyOf(facets);
  }

  public static SearchResult empty() {
    return new SearchResult(List.of(), 0, Map.of(), null);
  }
}
