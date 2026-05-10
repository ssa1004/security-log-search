package com.example.security.adapter.out.opensearch;

import com.example.security.application.port.out.EventSearchPort;
import com.example.security.application.query.SearchQuery;
import com.example.security.application.query.SearchResult;
import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.LogEvent;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpenSearch Java client 기반 검색 어댑터.
 *
 * <p>핵심 설계 (ADR-0007 멀티테넌트 격리 의 4 layer 중 (a) (d)):
 *
 * <ul>
 *   <li>인덱스 패턴은 read alias {@code events-{tenant}-read} — alias 가 그 tenant 인덱스만 가리킴
 *   <li>BoolQuery 의 filter 에 tenant.keyword=tenantId 강제 추가 — 사용자가 우회 불가
 *   <li>Resilience4j circuit breaker / retry / bulkhead 로 OpenSearch 장애 격리
 *   <li>termFilters / facet 의 field 이름은 {@link #ALLOWED_TERM_FIELDS} 화이트리스트로 검증 —
 *       사용자가 인덱스의 임의 필드 (내부 control 필드, 다른 tenant 인덱스의 _id 등) 를 지목
 *       할 수 없게 차단
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "security.opensearch.enabled", havingValue = "true", matchIfMissing = false)
public class OpenSearchEventSearchAdapter implements EventSearchPort {

  /**
   * termFilters / facet 으로 사용자에게 노출 가능한 ECS 필드.
   *
   * <p>OpenSearchIndexAdminAdapter 의 인덱스 템플릿에서 mapping 된 keyword 필드만 포함.
   * 화이트리스트가 아니면 OpenSearch 가 해당 필드 mapping 이 없을 때 ignore_unmapped 가
   * false 인 경우 query 가 실패할 수 있고, 외부에서 임의 필드 (예: tenant_id 자체를 또 비교
   * 하여 의도치 않은 결과) 를 끼워 넣는 표면을 차단한다.
   */
  static final java.util.Set<String> ALLOWED_TERM_FIELDS =
      java.util.Set.of(
          "event_kind",
          "event_category",
          "event_type",
          "event_action",
          "event_outcome",
          "severity",
          "source_ip",
          "destination_ip",
          "user_name",
          "host_name",
          "host_os");

  private final OpenSearchClient client;

  public OpenSearchEventSearchAdapter(OpenSearchClient client) {
    this.client = client;
  }

  /**
   * 사용자 노출용 field 이름 검증. 화이트리스트에 없으면 IllegalArgumentException.
   *
   * <p>{@code tenant_id}, {@code _id}, {@code _index} 등의 내부 / 격리 필드, 그리고 mapping
   * 안 된 임의 필드 ({@code admin_only} 등) 가 사용자 입력으로 들어오는 것을 차단.
   */
  static String requireAllowedField(String field) {
    if (field == null || !ALLOWED_TERM_FIELDS.contains(field)) {
      throw new IllegalArgumentException("허용되지 않는 검색 필드: " + field);
    }
    return field;
  }

  @Override
  @CircuitBreaker(name = "opensearch")
  @Retry(name = "opensearch")
  @Bulkhead(name = "opensearch")
  public SearchResult search(SearchQuery query) {
    var alias = "events-%s-read".formatted(query.tenantId().value());

    var bool = new BoolQuery.Builder();

    // (1) tenant filter 강제.
    bool.filter(
        Query.of(
            q ->
                q.term(
                    TermQuery.of(
                        t -> t.field("tenant_id.keyword").value(FieldValue.of(query.tenantId().value()))))));

    // (2) 사용자 query string (있으면).
    if (!query.luceneQueryString().isBlank() && !"*".equals(query.luceneQueryString())) {
      bool.must(Query.of(q -> q.queryString(qs -> qs.query(query.luceneQueryString()))));
    }

    // (3) term filters — field 이름은 화이트리스트로 검증.
    query
        .termFilters()
        .forEach(
            (field, value) -> bool.filter(termFilter(requireAllowedField(field), value)));

    // (4) 시간 범위.
    if (query.from() != null || query.to() != null) {
      var range = new RangeQuery.Builder().field("@timestamp");
      if (query.from() != null) range.gte(JsonData.of(query.from().toString()));
      if (query.to() != null) range.lt(JsonData.of(query.to().toString()));
      bool.filter(Query.of(q -> q.range(range.build())));
    }

    var requestBuilder =
        new SearchRequest.Builder()
            .index(alias)
            .query(Query.of(q -> q.bool(bool.build())))
            .size(query.size())
            .trackTotalHits(t -> t.enabled(true))
            .sort(s -> s.field(f -> f.field("@timestamp").order(SortOrder.Desc)))
            .sort(s -> s.field(f -> f.field("event_id.keyword").order(SortOrder.Desc)));

    // (5) facet (terms aggregation) — field 이름은 화이트리스트로 검증.
    Map<String, Aggregation> aggs = new HashMap<>();
    if (query.facetSize() > 0) {
      for (var facet : query.facets()) {
        var safeFacet = requireAllowedField(facet);
        aggs.put(
            safeFacet,
            Aggregation.of(
                a -> a.terms(t -> t.field(safeFacet + ".keyword").size(query.facetSize()))));
      }
    }
    if (!aggs.isEmpty()) {
      requestBuilder.aggregations(aggs);
    }

    // (6) cursor (search_after).
    if (query.cursor() != null && !query.cursor().isBlank()) {
      var parts = query.cursor().split("\\|", 2);
      if (parts.length == 2) {
        requestBuilder.searchAfter(parts[0], parts[1]);
      }
    }

    SearchResponse<Map> response;
    try {
      response = client.search(requestBuilder.build(), Map.class);
    } catch (IOException e) {
      throw new UncheckedIOException("OpenSearch 검색 실패", e);
    }

    var hits = mapHits(response, query.tenantId());
    var facets = mapFacets(response);
    var nextCursor = nextCursor(response);
    long total = response.hits().total() != null ? response.hits().total().value() : hits.size();
    return new SearchResult(hits, total, facets, nextCursor);
  }

  private static Query termFilter(String field, String value) {
    return Query.of(
        q ->
            q.term(
                TermQuery.of(
                    t -> t.field(field + ".keyword").value(FieldValue.of(value)))));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private List<LogEvent> mapHits(SearchResponse<Map> response, TenantId tenantId) {
    var out = new ArrayList<LogEvent>();
    for (var hit : response.hits().hits()) {
      var src = (Map<String, Object>) hit.source();
      if (src == null) continue;
      out.add(toLogEvent(src, tenantId));
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  static LogEvent toLogEvent(Map<String, Object> src, TenantId tenantId) {
    Map<String, Object> labels = (Map<String, Object>) src.getOrDefault("labels", Map.of());
    Map<String, String> labelStrings = new HashMap<>();
    labels.forEach((k, v) -> labelStrings.put(k, v == null ? "" : v.toString()));
    var ts = parseTs(src.get("@timestamp"));
    var ingested = parseTs(src.getOrDefault("ingested_at", src.get("@timestamp")));
    return new LogEvent(
        UUID.fromString(src.get("event_id").toString()),
        tenantId,
        ts,
        ingested,
        asString(src.get("event_kind"), "event"),
        asString(src.get("event_category"), "unknown"),
        asString(src.get("event_type"), "info"),
        asString(src.get("event_action"), null),
        asString(src.get("event_outcome"), "unknown"),
        Severity.valueOf(asString(src.get("severity"), "INFO")),
        asString(src.get("source_ip"), null),
        asInteger(src.get("source_port")),
        asString(src.get("destination_ip"), null),
        asInteger(src.get("destination_port")),
        asString(src.get("user_name"), null),
        asString(src.get("host_name"), null),
        asString(src.get("host_os"), null),
        asString(src.get("message"), ""),
        labelStrings);
  }

  private static Instant parseTs(Object v) {
    if (v == null) return Instant.now();
    return Instant.parse(v.toString());
  }

  private static String asString(Object v, String fallback) {
    return v == null ? fallback : v.toString();
  }

  private static Integer asInteger(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(v.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @SuppressWarnings({"rawtypes"})
  private Map<String, Map<String, Long>> mapFacets(SearchResponse<Map> response) {
    if (response.aggregations() == null || response.aggregations().isEmpty()) return Map.of();
    Map<String, Map<String, Long>> out = new LinkedHashMap<>();
    response
        .aggregations()
        .forEach(
            (name, agg) -> {
              if (agg.isSterms()) {
                var buckets = agg.sterms().buckets().array();
                Map<String, Long> bucketMap = new LinkedHashMap<>();
                buckets.stream()
                    .sorted(Comparator.<org.opensearch.client.opensearch._types.aggregations.StringTermsBucket>comparingLong(
                            b -> b.docCount())
                        .reversed())
                    .forEach(b -> bucketMap.put(b.key(), b.docCount()));
                out.put(name, bucketMap);
              }
            });
    return out;
  }

  @SuppressWarnings({"rawtypes"})
  private String nextCursor(SearchResponse<Map> response) {
    var hits = response.hits().hits();
    if (hits.isEmpty()) return null;
    var last = hits.get(hits.size() - 1);
    var sort = last.sort();
    if (sort == null || sort.size() < 2) return null;
    return sort.get(0) + "|" + sort.get(1);
  }
}
