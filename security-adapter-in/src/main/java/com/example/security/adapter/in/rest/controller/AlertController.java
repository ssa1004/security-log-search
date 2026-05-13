package com.example.security.adapter.in.rest.controller;

import com.example.security.adapter.in.rest.dto.AlertResponse;
import com.example.security.adapter.in.security.OperatorContextResolver;
import com.example.security.application.port.in.ListAlertsUseCase;
import com.example.security.application.port.in.ListAlertsUseCase.ListAlertsQuery;
import com.example.security.application.port.in.ListAlertsUseCase.Page;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.Alert.AlertStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** use case 6 — /api/v1/alerts list + acknowledge / resolve / false-positive. */
@RestController
@RequestMapping("/api/v1/alerts")
@Validated
public class AlertController {

  private final ListAlertsUseCase useCase;
  private final OperatorContextResolver operators;

  public AlertController(ListAlertsUseCase useCase, OperatorContextResolver operators) {
    this.useCase = useCase;
    this.operators = operators;
  }

  public record AlertPageResponse(java.util.List<AlertResponse> alerts, UUID nextCursor) {

    static AlertPageResponse from(Page page) {
      return new AlertPageResponse(
          page.alerts().stream().map(AlertResponse::from).toList(), page.nextCursor());
    }
  }

  @GetMapping
  public ResponseEntity<AlertPageResponse> list(
      @NotBlank @RequestParam String tenantId,
      @RequestParam(required = false) AlertStatus status,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "50") @Min(1) @Max(ListAlertsQuery.MAX_SIZE) int size,
      @RequestParam(required = false) UUID after) {
    var query =
        new ListAlertsQuery(
            TenantId.of(tenantId),
            Optional.ofNullable(status),
            Optional.ofNullable(from),
            Optional.ofNullable(to),
            size,
            Optional.ofNullable(after));
    return ResponseEntity.ok(AlertPageResponse.from(useCase.list(query, operators.currentOperator())));
  }

  @PostMapping("/{alertId}/ack")
  public ResponseEntity<AlertResponse> acknowledge(@PathVariable UUID alertId) {
    return ResponseEntity.ok(AlertResponse.from(useCase.acknowledge(alertId, operators.currentOperator())));
  }

  @PostMapping("/{alertId}/resolve")
  public ResponseEntity<AlertResponse> resolve(@PathVariable UUID alertId) {
    return ResponseEntity.ok(AlertResponse.from(useCase.resolve(alertId, operators.currentOperator())));
  }

  @PostMapping("/{alertId}/false-positive")
  public ResponseEntity<AlertResponse> markFalsePositive(@PathVariable UUID alertId) {
    return ResponseEntity.ok(
        AlertResponse.from(useCase.markFalsePositive(alertId, operators.currentOperator())));
  }
}
