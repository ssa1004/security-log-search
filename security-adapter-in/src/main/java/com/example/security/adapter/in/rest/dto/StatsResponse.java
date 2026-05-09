package com.example.security.adapter.in.rest.dto;

import com.example.security.application.query.StatsResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record StatsResponse(
    List<TimePoint> series, Map<String, List<TimePoint>> topGroups) {

  public static StatsResponse from(StatsResult r) {
    return new StatsResponse(
        r.series().stream().map(TimePoint::from).toList(),
        r.topGroups().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().stream().map(TimePoint::from).toList())));
  }

  public record TimePoint(Instant timestamp, long count, double p95LatencyMs) {

    static TimePoint from(StatsResult.TimeBucket b) {
      return new TimePoint(b.timestamp(), b.count(), b.p95LatencyMs());
    }
  }
}
