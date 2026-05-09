package com.example.security.adapter.in.rest.controller;

import com.example.security.adapter.in.rest.dto.TenantOnboardRequest;
import com.example.security.adapter.in.rest.dto.TenantResponse;
import com.example.security.adapter.in.security.OperatorContextResolver;
import com.example.security.application.port.in.OnboardTenantUseCase;
import com.example.security.application.port.in.OnboardTenantUseCase.OnboardCommand;
import com.example.security.domain.common.TenantId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** use case 9 — /api/v1/tenants. */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

  private final OnboardTenantUseCase useCase;
  private final OperatorContextResolver operators;

  public TenantController(OnboardTenantUseCase useCase, OperatorContextResolver operators) {
    this.useCase = useCase;
    this.operators = operators;
  }

  @PostMapping
  public ResponseEntity<TenantResponse> onboard(@Valid @RequestBody TenantOnboardRequest req) {
    var cmd =
        new OnboardCommand(
            TenantId.of(req.tenantId()),
            req.displayName(),
            req.retention(),
            req.hotRetention(),
            req.piiPolicy());
    var tenant = useCase.onboard(cmd, operators.currentOperator());
    return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(tenant));
  }

  @DeleteMapping("/{tenantId}")
  public ResponseEntity<Void> deactivate(@NotBlank @PathVariable String tenantId) {
    useCase.deactivate(TenantId.of(tenantId), operators.currentOperator());
    return ResponseEntity.noContent().build();
  }
}
