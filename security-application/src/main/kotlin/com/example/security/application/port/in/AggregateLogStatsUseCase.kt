package com.example.security.application.port.`in`

import com.example.security.application.query.StatsQuery
import com.example.security.application.query.StatsResult

/**
 * use case 3 — ClickHouse 시계열 / 집계 query.
 *
 * 대용량 raw event 의 GROUP BY / 사전집계 materialized view 활용.
 */
interface AggregateLogStatsUseCase {

    fun aggregate(query: StatsQuery, operator: OperatorContext): StatsResult
}
