package com.example.security.adapter.out.clickhouse

import com.example.security.application.port.out.EventStatsPort
import com.example.security.application.query.StatsQuery
import com.example.security.application.query.StatsResult
import com.example.security.application.query.StatsResult.TimeBucket
import io.github.resilience4j.bulkhead.annotation.Bulkhead
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * ClickHouse JDBC 기반 시계열 / 집계 query 어댑터.
 *
 * 설계 (ADR-0005 ClickHouse 스키마):
 *
 *  - raw 테이블: `events_raw` (MergeTree, PARTITION BY toYYYYMM(timestamp), ORDER BY
 *    (tenant_id, timestamp, event_id), ZSTD 압축)
 *  - 5분 사전집계: `events_5m_mv` (MaterializedView, AggregatingMergeTree)
 *  - 1시간 사전집계: `events_1h_mv`
 *
 * 본 어댑터는 query bucket 에 따라 적절한 source 테이블 선택 (raw / 5m / 1h MV) — query 비용
 * 최적화.
 */
@Component
@ConditionalOnProperty(
    name = ["security.clickhouse.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
open class ClickHouseEventStatsAdapter(
    @Qualifier("clickHouseDataSource") private val dataSource: DataSource,
) : EventStatsPort {

    @CircuitBreaker(name = "clickhouse")
    @Retry(name = "clickhouse")
    @Bulkhead(name = "clickhouse")
    override fun aggregate(query: StatsQuery): StatsResult {
        val sourceTable = pickSourceTable(query.bucket)
        return if (query.groupByField == null) {
            StatsResult(querySeries(query, sourceTable), emptyMap())
        } else {
            StatsResult(emptyList(), queryGrouped(query, sourceTable))
        }
    }

    private fun querySeries(query: StatsQuery, sourceTable: String): List<TimeBucket> {
        val bucketExpr = query.bucket.toClickHouseExpr("timestamp")
        // p95 는 항상 0 — ADR-0005 스키마에 latency 컬럼이 없어 quantile 의 입력을 상수 0 으로
        // 둔 placeholder 다 (TimeBucket.p95LatencyMs 자리만 채움). latency 컬럼 추가 시 0 을 그
        // 컬럼으로 교체하면 된다. 버그가 아니라 예약된 자리.
        val sql = StringBuilder("SELECT ")
            .append(bucketExpr)
            .append(" AS bucket_ts, count() AS cnt, quantile(0.95)(0) AS p95 FROM ")
            .append(sourceTable)
            .append(" WHERE tenant_id = ? AND timestamp >= ? AND timestamp < ?")
        appendTermFilters(sql, query)
        sql.append(" GROUP BY bucket_ts ORDER BY bucket_ts")

        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql.toString()).use { ps ->
                    bindCommon(ps, query)
                    ps.executeQuery().use { rs: ResultSet ->
                        val out = ArrayList<TimeBucket>()
                        while (rs.next()) {
                            out.add(
                                TimeBucket(
                                    toInstant(rs.getTimestamp("bucket_ts")),
                                    rs.getLong("cnt"),
                                    rs.getDouble("p95"),
                                ),
                            )
                        }
                        return out
                    }
                }
            }
        } catch (e: SQLException) {
            throw IllegalStateException("ClickHouse series query 실패", e)
        }
    }

    private fun queryGrouped(query: StatsQuery, sourceTable: String): Map<String, List<TimeBucket>> {
        val bucketExpr = query.bucket.toClickHouseExpr("timestamp")
        val groupCol = sanitizeColumn(query.groupByField!!)
        val sql = StringBuilder("SELECT ")
            .append(groupCol)
            .append(" AS grp, ")
            .append(bucketExpr)
            .append(" AS bucket_ts, count() AS cnt FROM ")
            .append(sourceTable)
            .append(" WHERE tenant_id = ? AND timestamp >= ? AND timestamp < ?")
        appendTermFilters(sql, query)
        sql.append(" GROUP BY grp, bucket_ts")
        sql.append(" ORDER BY grp, bucket_ts")
        sql.append(" LIMIT ").append(query.topN * 1000) // group * bucket 합 상한

        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql.toString()).use { ps ->
                    bindCommon(ps, query)
                    ps.executeQuery().use { rs: ResultSet ->
                        val grouped = LinkedHashMap<String, MutableList<TimeBucket>>()
                        while (rs.next()) {
                            val grp = rs.getString("grp")
                            grouped.getOrPut(grp) { ArrayList() }.add(
                                TimeBucket(
                                    toInstant(rs.getTimestamp("bucket_ts")),
                                    rs.getLong("cnt"),
                                    0.0,
                                ),
                            )
                        }
                        return grouped.mapValues { it.value.toList() }
                    }
                }
            }
        } catch (e: SQLException) {
            throw IllegalStateException("ClickHouse grouped query 실패", e)
        }
    }

    private fun appendTermFilters(sql: StringBuilder, query: StatsQuery) {
        for (field in query.termFilters.keys) {
            sql.append(" AND ").append(sanitizeColumn(field)).append(" = ?")
        }
    }

    private fun bindCommon(ps: PreparedStatement, query: StatsQuery) {
        var idx = 1
        ps.setString(idx++, query.tenantId.value)
        ps.setTimestamp(idx++, Timestamp.from(query.from))
        ps.setTimestamp(idx++, Timestamp.from(query.to))
        for (v in query.termFilters.values) {
            ps.setString(idx++, v)
        }
    }

    companion object {

        /** bucket 별 적절한 source 테이블 선택. raw 는 cost 가 큼 — 5분 단위는 5m_mv 사용. */
        @JvmStatic
        fun pickSourceTable(bucket: StatsQuery.Bucket): String =
            when (bucket) {
                StatsQuery.Bucket.FIVE_MINUTES -> "events_5m_mv"
                StatsQuery.Bucket.ONE_HOUR -> "events_1h_mv"
                StatsQuery.Bucket.ONE_DAY -> "events_raw"
            }

        /** SQL injection 방지 — 컬럼명에 영숫자 + underscore 만 허용. */
        @JvmStatic
        fun sanitizeColumn(column: String): String {
            require(column.matches(Regex("[a-zA-Z0-9_]+"))) { "허용되지 않는 컬럼명: $column" }
            return column
        }

        // bucket_ts 는 GROUP BY 결과로 항상 non-null. Java 어댑터는 ts.toInstant() 로
        // 그대로 NPE 가능하던 것을 Kotlin 에서는 !! 로 명시.
        private fun toInstant(ts: Timestamp?): Instant = ts!!.toInstant()
    }
}
