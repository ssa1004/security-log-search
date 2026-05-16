package com.example.security.application.query

import com.example.security.domain.common.TenantId
import java.time.Instant

/**
 * 시계열 / 집계 query — ClickHouse 에서 실행.
 *
 * OpenSearch 가 적합한 full-text 검색과 달리 본 query 는 5분 / 1시간 / 1일 단위 bucket 으로
 * 묶은 카운트 / 평균 / percentile 을 가져오는 용도다.
 *
 * @property bucket bucket 단위
 * @property groupByField top-N 그룹 (예: "source_ip" / "event_action"). null 이면 전체 카운트만.
 * @property topN groupByField 가 있을 때 top-N 크기
 * @property termFilters 정확한 매치 필터 (event_outcome=failure 등)
 */
@JvmRecord
data class StatsQuery(
    val tenantId: TenantId,
    val from: Instant,
    val to: Instant,
    val bucket: Bucket,
    val groupByField: String?,
    val topN: Int,
    val termFilters: Map<String, String>,
) {

    init {
        require(!from.isAfter(to)) { "from > to" }
        require(groupByField == null || topN in 1..1000) { "topN 은 1~1000" }
    }

    enum class Bucket {
        FIVE_MINUTES,
        ONE_HOUR,
        ONE_DAY;

        /** ClickHouse 의 toStartOfInterval / toStartOfHour 등 함수 호출에 쓸 SQL fragment. */
        fun toClickHouseExpr(column: String): String =
            when (this) {
                FIVE_MINUTES -> "toStartOfInterval($column, INTERVAL 5 MINUTE)"
                ONE_HOUR -> "toStartOfHour($column)"
                ONE_DAY -> "toStartOfDay($column)"
            }
    }
}
