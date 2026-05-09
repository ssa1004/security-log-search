package com.example.security.adapter.in.rest.controller;

import com.example.security.adapter.in.security.OperatorContextResolver;
import com.example.security.application.port.in.ManageOpenSearchIndexUseCase;
import com.example.security.application.port.in.ManageOpenSearchIndexUseCase.RolloverResult;
import com.example.security.domain.common.TenantId;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** use case 7 — /api/v1/admin/indices/{tenantId}/* — admin only. */
@RestController
@RequestMapping("/api/v1/admin/indices")
public class IndexAdminController {

  private final ManageOpenSearchIndexUseCase useCase;
  private final OperatorContextResolver operators;

  public IndexAdminController(ManageOpenSearchIndexUseCase useCase, OperatorContextResolver operators) {
    this.useCase = useCase;
    this.operators = operators;
  }

  @PostMapping("/{tenantId}")
  public ResponseEntity<Void> create(@NotBlank @PathVariable String tenantId) {
    useCase.createInitialIndex(TenantId.of(tenantId), operators.currentOperator());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/{tenantId}/rollover")
  public ResponseEntity<RolloverResult> rollover(@NotBlank @PathVariable String tenantId) {
    return ResponseEntity.ok(useCase.triggerRollover(TenantId.of(tenantId), operators.currentOperator()));
  }

  @PostMapping("/{tenantId}/ilm")
  public ResponseEntity<Void> applyIlm(@NotBlank @PathVariable String tenantId) {
    useCase.applyIlmPolicy(TenantId.of(tenantId), operators.currentOperator());
    return ResponseEntity.accepted().build();
  }
}
