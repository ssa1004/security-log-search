package com.example.security.adapter.`in`.rest.dto

import com.example.security.application.query.StatsResult
import java.time.Instant

/**
 * GET /api/v1/stats 응답 — 시계열 + 그룹별 시계열.
 *
 * application layer 의 StatsResult 를 wire format 으로 평탄화한다.
 */
@JvmRecord
data class StatsResponse(
    val series: List<TimePoint>,
    val topGroups: Map<String, List<TimePoint>>,
) {

    @JvmRecord
    data class TimePoint(val timestamp: Instant, val count: Long, val p95LatencyMs: Double) {
        companion object {
            @JvmStatic
            fun from(b: StatsResult.TimeBucket): TimePoint =
                TimePoint(b.timestamp, b.count, b.p95LatencyMs)
        }
    }

    companion object {
        @JvmStatic
        fun from(r: StatsResult): StatsResponse =
            StatsResponse(
                r.series.map { TimePoint.from(it) },
                r.topGroups.mapValues { (_, list) -> list.map { TimePoint.from(it) } },
            )
    }
}
