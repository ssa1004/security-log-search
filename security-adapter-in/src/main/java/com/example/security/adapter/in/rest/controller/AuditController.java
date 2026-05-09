package com.example.security.adapter.in.rest.controller;

import com.example.security.adapter.in.rest.dto.AuditEntryResponse;
import com.example.security.adapter.in.security.OperatorContextResolver;
import com.example.security.application.port.in.QueryAuditLogUseCase;
import com.example.security.application.port.in.QueryAuditLogUseCase.AuditQuery;
import com.example.security.domain.audit.AuditEntry.AuditAction;
import com.example.security.domain.common.TenantId;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** use case 8 — /api/v1/audit. */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

  private final QueryAuditLogUseCase useCase;
  private final OperatorContextResolver operators;

  public AuditController(QueryAuditLogUseCase useCase, OperatorContextResolver operators) {
    this.useCase = useCase;
    this.operators = operators;
  }

  @GetMapping
  public ResponseEntity<List<AuditEntryResponse>> query(
      @NotBlank @RequestParam String tenantId,
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) AuditAction action,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "100") int size) {
    var query =
        new AuditQuery(
            TenantId.of(tenantId),
            Optional.ofNullable(actor),
            Optional.ofNullable(action),
            Optional.ofNullable(from),
            Optional.ofNullable(to),
            size);
    return ResponseEntity.ok(
        useCase.query(query, operators.currentOperator()).stream()
            .map(AuditEntryResponse::from)
            .toList());
  }
}
