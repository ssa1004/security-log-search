package com.example.security.application.query

import java.time.Instant

/**
 * 시계열 / 집계 결과.
 *
 * @property series 시계열 — bucket 시각별 카운트 (groupByField 없는 경우)
 * @property topGroups groupByField 가 있을 때만 채워짐 — 그룹 키 → bucket 시각별 카운트
 */
@JvmRecord
data class StatsResult(
    val series: List<TimeBucket>,
    val topGroups: Map<String, List<TimeBucket>>,
) {

    @JvmRecord
    data class TimeBucket(val timestamp: Instant, val count: Long, val p95LatencyMs: Double)

    companion object {
        @JvmStatic
        fun empty(): StatsResult = StatsResult(emptyList(), emptyMap())
    }
}
