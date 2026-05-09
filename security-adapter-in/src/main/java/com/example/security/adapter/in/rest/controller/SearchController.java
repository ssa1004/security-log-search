package com.example.security.adapter.in.rest.controller;

import com.example.security.adapter.in.rest.dto.SearchRequest;
import com.example.security.adapter.in.rest.dto.SearchResponse;
import com.example.security.adapter.in.security.OperatorContextResolver;
import com.example.security.application.port.in.SearchLogEventsUseCase;
import com.example.security.application.query.SearchQuery;
import com.example.security.domain.common.TenantId;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** use case 2 — POST /api/v1/search. */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

  private final SearchLogEventsUseCase useCase;
  private final OperatorContextResolver operators;

  public SearchController(SearchLogEventsUseCase useCase, OperatorContextResolver operators) {
    this.useCase = useCase;
    this.operators = operators;
  }

  @PostMapping
  public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest req) {
    var query =
        new SearchQuery(
            TenantId.of(req.tenantId()),
            req.query() == null ? "" : req.query(),
            req.filters() == null ? Map.of() : req.filters(),
            req.from(),
            req.to(),
            req.facets() == null ? List.of() : req.facets(),
            req.facetSize() == null ? 0 : req.facetSize(),
            req.size() == null ? 50 : req.size(),
            req.cursor());
    var result = useCase.search(query, operators.currentOperator());
    return ResponseEntity.ok(SearchResponse.from(result));
  }
}
