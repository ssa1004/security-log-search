package com.example.security.application.query;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 시계열 / 집계 결과.
 *
 * @param series 시계열 — bucket 시각별 카운트 (groupByField 없는 경우)
 * @param topGroups groupByField 가 있을 때만 채워짐 — 그룹 키 → bucket 시각별 카운트
 */
public record StatsResult(
    List<TimeBucket> series, Map<String, List<TimeBucket>> topGroups) {

  public StatsResult {
    Objects.requireNonNull(series);
    series = List.copyOf(series);
    topGroups = topGroups == null ? Map.of() : Map.copyOf(topGroups);
  }

  public record TimeBucket(Instant timestamp, long count, double p95LatencyMs) {}

  public static StatsResult empty() {
    return new StatsResult(List.of(), Map.of());
  }
}
