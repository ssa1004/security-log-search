package com.example.security.adapter.in.rest.controller;

import com.example.security.adapter.in.rest.dto.StatsResponse;
import com.example.security.adapter.in.security.OperatorContextResolver;
import com.example.security.application.port.in.AggregateLogStatsUseCase;
import com.example.security.application.query.StatsQuery;
import com.example.security.application.query.StatsQuery.Bucket;
import com.example.security.domain.common.TenantId;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** use case 3 — GET /api/v1/stats. */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

  private final AggregateLogStatsUseCase useCase;
  private final OperatorContextResolver operators;

  public StatsController(AggregateLogStatsUseCase useCase, OperatorContextResolver operators) {
    this.useCase = useCase;
    this.operators = operators;
  }

  @GetMapping
  public ResponseEntity<StatsResponse> stats(
      @NotBlank @RequestParam String tenantId,
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(defaultValue = "ONE_HOUR") Bucket bucket,
      @RequestParam(required = false) String groupByField,
      @RequestParam(defaultValue = "10") int topN,
      @RequestParam(required = false) Map<String, String> filter) {
    Map<String, String> termFilters = filter == null ? new HashMap<>() : new HashMap<>(filter);
    // 위에서 filter 자체를 query string parameter 로 받아 자동 매핑되었으나 reserved param 제거.
    termFilters.remove("tenantId");
    termFilters.remove("from");
    termFilters.remove("to");
    termFilters.remove("bucket");
    termFilters.remove("groupByField");
    termFilters.remove("topN");

    var query = new StatsQuery(TenantId.of(tenantId), from, to, bucket, groupByField, topN, termFilters);
    var result = useCase.aggregate(query, operators.currentOperator());
    return ResponseEntity.ok(StatsResponse.from(result));
  }
}
