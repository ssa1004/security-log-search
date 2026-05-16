package com.example.security.application.port.out

import com.example.security.application.query.StatsQuery
import com.example.security.application.query.StatsResult

/** ClickHouse 시계열 / 집계 query 의 outbound port. */
interface EventStatsPort {

    fun aggregate(query: StatsQuery): StatsResult
}
