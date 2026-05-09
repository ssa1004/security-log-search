package com.example.security.application.query;

import com.example.security.domain.common.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 시계열 / 집계 query — ClickHouse 에서 실행.
 *
 * <p>OpenSearch 가 적합한 full-text 검색과 달리 본 query 는 5분 / 1시간 / 1일 단위 bucket 으로
 * 묶은 카운트 / 평균 / percentile 을 가져오는 용도다.
 *
 * @param bucket bucket 단위
 * @param groupByField top-N 그룹 (예: "source_ip" / "event_action"). null 이면 전체 카운트만.
 * @param topN groupByField 가 있을 때 top-N 크기
 * @param termFilters 정확한 매치 필터 (event_outcome=failure 등)
 */
public record StatsQuery(
    TenantId tenantId,
    Instant from,
    Instant to,
    Bucket bucket,
    String groupByField,
    int topN,
    Map<String, String> termFilters) {

  public StatsQuery {
    Objects.requireNonNull(tenantId);
    Objects.requireNonNull(from);
    Objects.requireNonNull(to);
    Objects.requireNonNull(bucket);
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("from > to");
    }
    termFilters = termFilters == null ? Map.of() : Map.copyOf(termFilters);
    if (groupByField != null && (topN < 1 || topN > 1000)) {
      throw new IllegalArgumentException("topN 은 1~1000");
    }
  }

  public enum Bucket {
    FIVE_MINUTES,
    ONE_HOUR,
    ONE_DAY;

    /** ClickHouse 의 toStartOfInterval / toStartOfHour 등 함수 호출에 쓸 SQL fragment. */
    public String toClickHouseExpr(String column) {
      return switch (this) {
        case FIVE_MINUTES -> "toStartOfInterval(" + column + ", INTERVAL 5 MINUTE)";
        case ONE_HOUR -> "toStartOfHour(" + column + ")";
        case ONE_DAY -> "toStartOfDay(" + column + ")";
      };
    }
  }
}
