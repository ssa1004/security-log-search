package com.example.security.adapter.out.clickhouse;

import com.example.security.application.port.out.EventStatsPort;
import com.example.security.application.query.StatsQuery;
import com.example.security.application.query.StatsResult;
import com.example.security.application.query.StatsResult.TimeBucket;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * ClickHouse JDBC 기반 시계열 / 집계 query 어댑터.
 *
 * <p>설계 (ADR-0005 ClickHouse 스키마):
 *
 * <ul>
 *   <li>raw 테이블: {@code events_raw} (MergeTree, PARTITION BY toYYYYMM(timestamp), ORDER BY
 *       (tenant_id, timestamp, event_id), ZSTD 압축)
 *   <li>5분 사전집계: {@code events_5m_mv} (MaterializedView, AggregatingMergeTree)
 *   <li>1시간 사전집계: {@code events_1h_mv}
 * </ul>
 *
 * <p>본 어댑터는 query bucket 에 따라 적절한 source 테이블 선택 (raw / 5m / 1h MV) — query 비용
 * 최적화.
 */
@Component
public class ClickHouseEventStatsAdapter implements EventStatsPort {

  private final DataSource dataSource;

  public ClickHouseEventStatsAdapter(@Qualifier("clickHouseDataSource") DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  @CircuitBreaker(name = "clickhouse")
  @Retry(name = "clickhouse")
  @Bulkhead(name = "clickhouse")
  public StatsResult aggregate(StatsQuery query) {
    var sourceTable = pickSourceTable(query.bucket());
    if (query.groupByField() == null) {
      return new StatsResult(querySeries(query, sourceTable), Map.of());
    } else {
      return new StatsResult(List.of(), queryGrouped(query, sourceTable));
    }
  }

  /** bucket 별 적절한 source 테이블 선택. raw 는 cost 가 큼 — 5분 단위는 5m_mv 사용. */
  static String pickSourceTable(StatsQuery.Bucket bucket) {
    return switch (bucket) {
      case FIVE_MINUTES -> "events_5m_mv";
      case ONE_HOUR -> "events_1h_mv";
      case ONE_DAY -> "events_raw";
    };
  }

  private List<TimeBucket> querySeries(StatsQuery query, String sourceTable) {
    var bucketExpr = query.bucket().toClickHouseExpr("timestamp");
    var sql =
        new StringBuilder("SELECT ")
            .append(bucketExpr)
            .append(" AS bucket_ts, count() AS cnt, quantile(0.95)(0) AS p95 FROM ")
            .append(sourceTable)
            .append(" WHERE tenant_id = ? AND timestamp >= ? AND timestamp < ?");
    appendTermFilters(sql, query);
    sql.append(" GROUP BY bucket_ts ORDER BY bucket_ts");

    try (var conn = dataSource.getConnection();
        var ps = conn.prepareStatement(sql.toString())) {
      bindCommon(ps, query);
      try (var rs = ps.executeQuery()) {
        var out = new ArrayList<TimeBucket>();
        while (rs.next()) {
          out.add(
              new TimeBucket(
                  toInstant(rs.getTimestamp("bucket_ts")), rs.getLong("cnt"), rs.getDouble("p95")));
        }
        return out;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("ClickHouse series query 실패", e);
    }
  }

  private Map<String, List<TimeBucket>> queryGrouped(StatsQuery query, String sourceTable) {
    var bucketExpr = query.bucket().toClickHouseExpr("timestamp");
    var groupCol = sanitizeColumn(query.groupByField());
    var sql =
        new StringBuilder("SELECT ")
            .append(groupCol)
            .append(" AS grp, ")
            .append(bucketExpr)
            .append(" AS bucket_ts, count() AS cnt FROM ")
            .append(sourceTable)
            .append(" WHERE tenant_id = ? AND timestamp >= ? AND timestamp < ?");
    appendTermFilters(sql, query);
    sql.append(" GROUP BY grp, bucket_ts");
    sql.append(" ORDER BY grp, bucket_ts");
    sql.append(" LIMIT ").append(query.topN() * 1000); // group * bucket 합 상한

    try (var conn = dataSource.getConnection();
        var ps = conn.prepareStatement(sql.toString())) {
      bindCommon(ps, query);
      try (var rs = ps.executeQuery()) {
        Map<String, List<TimeBucket>> grouped = new LinkedHashMap<>();
        while (rs.next()) {
          var grp = rs.getString("grp");
          grouped
              .computeIfAbsent(grp, k -> new ArrayList<>())
              .add(new TimeBucket(toInstant(rs.getTimestamp("bucket_ts")), rs.getLong("cnt"), 0.0));
        }
        return grouped;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("ClickHouse grouped query 실패", e);
    }
  }

  private void appendTermFilters(StringBuilder sql, StatsQuery query) {
    for (var field : query.termFilters().keySet()) {
      sql.append(" AND ").append(sanitizeColumn(field)).append(" = ?");
    }
  }

  private void bindCommon(PreparedStatement ps, StatsQuery query) throws SQLException {
    int idx = 1;
    ps.setString(idx++, query.tenantId().value());
    ps.setTimestamp(idx++, Timestamp.from(query.from()));
    ps.setTimestamp(idx++, Timestamp.from(query.to()));
    for (var v : query.termFilters().values()) {
      ps.setString(idx++, v);
    }
  }

  /** SQL injection 방지 — 컬럼명에 영숫자 + underscore 만 허용. */
  static String sanitizeColumn(String column) {
    if (!column.matches("[a-zA-Z0-9_]+")) {
      throw new IllegalArgumentException("허용되지 않는 컬럼명: " + column);
    }
    return column;
  }

  private static Instant toInstant(Timestamp ts) {
    return ts == null ? null : ts.toInstant();
  }
}
