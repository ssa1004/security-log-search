package com.example.security.adapter.out.opensearch

import com.example.security.application.port.out.EventSearchPort
import com.example.security.application.query.SearchQuery
import com.example.security.application.query.SearchResult
import com.example.security.domain.common.Severity
import com.example.security.domain.common.TenantId
import com.example.security.domain.event.LogEvent
import io.github.resilience4j.bulkhead.annotation.Bulkhead
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import java.io.IOException
import java.io.UncheckedIOException
import java.time.Instant
import java.util.UUID
import org.opensearch.client.json.JsonData
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch._types.SortOrder
import org.opensearch.client.opensearch._types.aggregations.Aggregation
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery
import org.opensearch.client.opensearch._types.query_dsl.Query
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery
import org.opensearch.client.opensearch._types.query_dsl.TermQuery
import org.opensearch.client.opensearch.core.SearchRequest
import org.opensearch.client.opensearch.core.SearchResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * OpenSearch Java client 기반 검색 어댑터.
 *
 * 핵심 설계 (ADR-0007 멀티테넌트 격리 의 4 layer 중 (a) (d)):
 *
 *  - 인덱스 패턴은 read alias `events-{tenant}-read` — alias 가 그 tenant 인덱스만 가리킴
 *  - BoolQuery 의 filter 에 tenant.keyword=tenantId 강제 추가 — 사용자가 우회 불가
 *  - Resilience4j circuit breaker / retry / bulkhead 로 OpenSearch 장애 격리
 *  - termFilters / facet 의 field 이름은 [ALLOWED_TERM_FIELDS] 화이트리스트로 검증 — 사용자가
 *    인덱스의 임의 필드 (내부 control 필드, 다른 tenant 인덱스의 _id 등) 를 지목할 수 없게 차단
 */
@Component
@ConditionalOnProperty(
    name = ["security.opensearch.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
open class OpenSearchEventSearchAdapter(
    private val client: OpenSearchClient,
) : EventSearchPort {

    @CircuitBreaker(name = "opensearch")
    @Retry(name = "opensearch")
    @Bulkhead(name = "opensearch")
    override fun search(query: SearchQuery): SearchResult {
        val alias = "events-${query.tenantId.value}-read"

        val bool = BoolQuery.Builder()

        // (1) tenant filter 강제.
        bool.filter(
            Query.of { q ->
                q.term(
                    TermQuery.of { t ->
                        t.field("tenant_id.keyword").value(FieldValue.of(query.tenantId.value))
                    },
                )
            },
        )

        // (2) 사용자 query string (있으면).
        if (query.luceneQueryString.isNotBlank() && "*" != query.luceneQueryString) {
            bool.must(Query.of { q -> q.queryString { qs -> qs.query(query.luceneQueryString) } })
        }

        // (3) term filters — field 이름은 화이트리스트로 검증.
        query.termFilters.forEach { (field, value) ->
            bool.filter(termFilter(requireAllowedField(field), value))
        }

        // (4) 시간 범위.
        if (query.from != null || query.to != null) {
            val range = RangeQuery.Builder().field("@timestamp")
            query.from?.let { range.gte(JsonData.of(it.toString())) }
            query.to?.let { range.lt(JsonData.of(it.toString())) }
            bool.filter(Query.of { q -> q.range(range.build()) })
        }

        val requestBuilder = SearchRequest.Builder()
            .index(alias)
            .query(Query.of { q -> q.bool(bool.build()) })
            .size(query.size)
            .trackTotalHits { it.enabled(true) }
            .sort { s -> s.field { f -> f.field("@timestamp").order(SortOrder.Desc) } }
            .sort { s -> s.field { f -> f.field("event_id.keyword").order(SortOrder.Desc) } }

        // (5) facet (terms aggregation) — field 이름은 화이트리스트로 검증.
        val aggs = HashMap<String, Aggregation>()
        if (query.facetSize > 0) {
            for (facet in query.facets) {
                val safeFacet = requireAllowedField(facet)
                aggs[safeFacet] = Aggregation.of { a ->
                    a.terms { t -> t.field("$safeFacet.keyword").size(query.facetSize) }
                }
            }
        }
        if (aggs.isNotEmpty()) {
            requestBuilder.aggregations(aggs)
        }

        // (6) cursor (search_after).
        if (!query.cursor.isNullOrBlank()) {
            val parts = query.cursor!!.split("|", limit = 2)
            if (parts.size == 2) {
                requestBuilder.searchAfter(parts[0], parts[1])
            }
        }

        // OpenSearch Java client 는 hit source 를 typed class 로 deserialize 하므로
        // 임의 JSON 을 받기 위해 raw Map.class 를 넘긴다.
        @Suppress("UNCHECKED_CAST")
        val response: SearchResponse<Map<*, *>> = try {
            client.search(requestBuilder.build(), Map::class.java) as SearchResponse<Map<*, *>>
        } catch (e: IOException) {
            throw UncheckedIOException("OpenSearch 검색 실패", e)
        }

        val hits = mapHits(response, query.tenantId)
        val facets = mapFacets(response)
        val nextCursor = nextCursor(response)
        val total = response.hits().total()?.value() ?: hits.size.toLong()
        return SearchResult(hits, total, facets, nextCursor)
    }

    private fun mapHits(response: SearchResponse<Map<*, *>>, tenantId: TenantId): List<LogEvent> {
        val out = ArrayList<LogEvent>()
        for (hit in response.hits().hits()) {
            @Suppress("UNCHECKED_CAST")
            val src = hit.source() as Map<String, Any?>? ?: continue
            out.add(toLogEvent(src, tenantId))
        }
        return out
    }

    private fun mapFacets(response: SearchResponse<Map<*, *>>): Map<String, Map<String, Long>> {
        val aggregations = response.aggregations()
        if (aggregations == null || aggregations.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Map<String, Long>>()
        aggregations.forEach { (name, agg) ->
            if (agg.isSterms) {
                val buckets = agg.sterms().buckets().array()
                val bucketMap = LinkedHashMap<String, Long>()
                buckets
                    .sortedByDescending { it.docCount() }
                    .forEach { bucketMap[it.key()] = it.docCount() }
                out[name] = bucketMap
            }
        }
        return out
    }

    private fun nextCursor(response: SearchResponse<Map<*, *>>): String? {
        val hits = response.hits().hits()
        if (hits.isEmpty()) return null
        val last = hits[hits.size - 1]
        val sort = last.sort() ?: return null
        if (sort.size < 2) return null
        return "${sort[0]}|${sort[1]}"
    }

    companion object {

        /**
         * termFilters / facet 으로 사용자에게 노출 가능한 ECS 필드.
         *
         * OpenSearchIndexAdminAdapter 의 인덱스 템플릿에서 mapping 된 keyword 필드만 포함.
         * 화이트리스트가 아니면 OpenSearch 가 해당 필드 mapping 이 없을 때 ignore_unmapped 가
         * false 인 경우 query 가 실패할 수 있고, 외부에서 임의 필드 (예: tenant_id 자체를 또 비교
         * 하여 의도치 않은 결과) 를 끼워 넣는 표면을 차단한다.
         */
        @JvmField
        internal val ALLOWED_TERM_FIELDS: Set<String> = setOf(
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
            "host_os",
        )

        /**
         * 사용자 노출용 field 이름 검증. 화이트리스트에 없으면 IllegalArgumentException.
         *
         * `tenant_id`, `_id`, `_index` 등의 내부 / 격리 필드, 그리고 mapping 안 된 임의 필드
         * (`admin_only` 등) 가 사용자 입력으로 들어오는 것을 차단.
         */
        @JvmStatic
        fun requireAllowedField(field: String?): String {
            require(field != null && ALLOWED_TERM_FIELDS.contains(field)) {
                "허용되지 않는 검색 필드: $field"
            }
            return field
        }

        @JvmStatic
        internal fun toLogEvent(src: Map<String, Any?>, tenantId: TenantId): LogEvent {
            @Suppress("UNCHECKED_CAST")
            val labels = (src.getOrDefault("labels", emptyMap<String, Any?>()) as Map<String, Any?>)
            val labelStrings = HashMap<String, String>()
            labels.forEach { (k, v) -> labelStrings[k] = v?.toString() ?: "" }
            val ts = parseTs(src["@timestamp"])
            val ingested = parseTs(src.getOrDefault("ingested_at", src["@timestamp"]))
            return LogEvent(
                UUID.fromString(src["event_id"].toString()),
                tenantId,
                ts,
                ingested,
                asString(src["event_kind"], "event"),
                asString(src["event_category"], "unknown"),
                asString(src["event_type"], "info"),
                asString(src["event_action"], null),
                asString(src["event_outcome"], "unknown"),
                Severity.valueOf(asString(src["severity"], "INFO")!!),
                asString(src["source_ip"], null),
                asInteger(src["source_port"]),
                asString(src["destination_ip"], null),
                asInteger(src["destination_port"]),
                asString(src["user_name"], null),
                asString(src["host_name"], null),
                asString(src["host_os"], null),
                asString(src["message"], ""),
                labelStrings,
            )
        }

        private fun termFilter(field: String, value: String): Query =
            Query.of { q ->
                q.term(TermQuery.of { t -> t.field("$field.keyword").value(FieldValue.of(value)) })
            }

        private fun parseTs(v: Any?): Instant {
            if (v == null) return Instant.now()
            return Instant.parse(v.toString())
        }

        private fun asString(v: Any?, fallback: String?): String? = v?.toString() ?: fallback

        private fun asInteger(v: Any?): Int? = when (v) {
            null -> null
            is Number -> v.toInt()
            else -> try {
                Integer.parseInt(v.toString())
            } catch (_: NumberFormatException) {
                null
            }
        }
    }
}
