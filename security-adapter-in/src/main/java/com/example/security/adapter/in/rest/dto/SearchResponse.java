package com.example.security.adapter.in.rest.dto;

import com.example.security.application.query.SearchResult;
import com.example.security.domain.event.LogEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SearchResponse(
    List<EventSummary> hits,
    long totalHits,
    Map<String, Map<String, Long>> facets,
    String nextCursor) {

  public static SearchResponse from(SearchResult result) {
    return new SearchResponse(
        result.hits().stream().map(EventSummary::from).toList(),
        result.totalHits(),
        result.facets(),
        result.nextCursor());
  }

  public record EventSummary(
      UUID eventId,
      String tenantId,
      Instant timestamp,
      String severity,
      String category,
      String action,
      String outcome,
      String sourceIp,
      String destinationIp,
      String userName,
      String hostName,
      String message) {

    static EventSummary from(LogEvent e) {
      return new EventSummary(
          e.eventId(),
          e.tenantId().value(),
          e.timestamp(),
          e.severity().name(),
          e.eventCategory(),
          e.eventAction(),
          e.eventOutcome(),
          e.sourceIp(),
          e.destinationIp(),
          e.userName(),
          e.hostName(),
          e.message());
    }
  }
}
