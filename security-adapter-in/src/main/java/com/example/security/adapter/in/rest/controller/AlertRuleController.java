package com.example.security.adapter.in.rest.controller;

import com.example.security.adapter.in.rest.dto.AlertRuleRequest;
import com.example.security.adapter.in.security.OperatorContextResolver;
import com.example.security.application.port.in.DefineAlertRuleUseCase;
import com.example.security.application.port.in.DefineAlertRuleUseCase.CreateRuleCommand;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.AlertRule;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** use case 4 — /api/v1/alert-rules CRUD. */
@RestController
@RequestMapping("/api/v1/alert-rules")
public class AlertRuleController {

  private final DefineAlertRuleUseCase useCase;
  private final OperatorContextResolver operators;

  public AlertRuleController(DefineAlertRuleUseCase useCase, OperatorContextResolver operators) {
    this.useCase = useCase;
    this.operators = operators;
  }

  @PostMapping
  public ResponseEntity<AlertRule> create(@Valid @RequestBody AlertRuleRequest req) {
    var cmd = toCommand(req);
    var rule = useCase.create(cmd, operators.currentOperator());
    return ResponseEntity.status(HttpStatus.CREATED).body(rule);
  }

  @PutMapping("/{ruleId}")
  public ResponseEntity<AlertRule> update(
      @PathVariable UUID ruleId, @Valid @RequestBody AlertRuleRequest req) {
    var rule = useCase.update(ruleId, toCommand(req), operators.currentOperator());
    return ResponseEntity.ok(rule);
  }

  @DeleteMapping("/{ruleId}")
  public ResponseEntity<Void> delete(@PathVariable UUID ruleId) {
    useCase.delete(ruleId, operators.currentOperator());
    return ResponseEntity.noContent().build();
  }

  private static CreateRuleCommand toCommand(AlertRuleRequest req) {
    return new CreateRuleCommand(
        TenantId.of(req.tenantId()),
        req.name(),
        req.description(),
        req.type(),
        req.filterCategory(),
        req.filterAction(),
        req.filterOutcome(),
        req.groupByField(),
        req.threshold(),
        req.window(),
        req.severity(),
        req.enabled());
  }
}
