package com.example.security.adapter.out.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.security.application.query.StatsQuery;
import org.junit.jupiter.api.Test;

class ClickHouseEventStatsAdapterTest {

  @Test
  void bucket_별_적절한_source_테이블_선택() {
    assertThat(ClickHouseEventStatsAdapter.pickSourceTable(StatsQuery.Bucket.FIVE_MINUTES))
        .isEqualTo("events_5m_mv");
    assertThat(ClickHouseEventStatsAdapter.pickSourceTable(StatsQuery.Bucket.ONE_HOUR))
        .isEqualTo("events_1h_mv");
    assertThat(ClickHouseEventStatsAdapter.pickSourceTable(StatsQuery.Bucket.ONE_DAY))
        .isEqualTo("events_raw");
  }

  @Test
  void column_sanitize_허용_문자만() {
    assertThat(ClickHouseEventStatsAdapter.sanitizeColumn("source_ip")).isEqualTo("source_ip");
    assertThat(ClickHouseEventStatsAdapter.sanitizeColumn("event_action")).isEqualTo("event_action");
    assertThat(ClickHouseEventStatsAdapter.sanitizeColumn("u123_x")).isEqualTo("u123_x");
  }

  @Test
  void column_sanitize_SQL_injection_차단() {
    assertThatThrownBy(() -> ClickHouseEventStatsAdapter.sanitizeColumn("source_ip; DROP TABLE x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ClickHouseEventStatsAdapter.sanitizeColumn("--comment"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ClickHouseEventStatsAdapter.sanitizeColumn("a b"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void bucket_to_clickhouse_expr() {
    assertThat(StatsQuery.Bucket.FIVE_MINUTES.toClickHouseExpr("ts"))
        .isEqualTo("toStartOfInterval(ts, INTERVAL 5 MINUTE)");
    assertThat(StatsQuery.Bucket.ONE_HOUR.toClickHouseExpr("ts")).isEqualTo("toStartOfHour(ts)");
    assertThat(StatsQuery.Bucket.ONE_DAY.toClickHouseExpr("ts")).isEqualTo("toStartOfDay(ts)");
  }
}
