package com.example.security.adapter.in.rest.controller;

import com.example.security.adapter.in.rest.dto.SigmaImportRequest;
import com.example.security.adapter.in.rest.dto.SigmaImportResponse;
import com.example.security.adapter.in.rest.dto.SigmaRuleResponse;
import com.example.security.adapter.in.security.OperatorContextResolver;
import com.example.security.application.port.in.ImportSigmaRuleUseCase;
import com.example.security.application.port.in.ImportSigmaRuleUseCase.ImportCommand;
import com.example.security.application.port.in.ImportSigmaRuleUseCase.ImportResult;
import com.example.security.application.port.in.ListImportedSigmaRulesUseCase;
import com.example.security.domain.common.TenantId;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * use case 9, 10 — Sigma 룰 import / 조회 endpoint.
 *
 * <ul>
 *   <li>POST /api/v1/sigma-rules — YAML import → AlertRule 변환
 *   <li>GET  /api/v1/sigma-rules?tenantId=acme — import 한 Sigma 룰 메타데이터 목록
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/sigma-rules")
public class SigmaRuleController {

  private final ImportSigmaRuleUseCase importUseCase;
  private final ListImportedSigmaRulesUseCase listUseCase;
  private final OperatorContextResolver operators;

  public SigmaRuleController(
      ImportSigmaRuleUseCase importUseCase,
      ListImportedSigmaRulesUseCase listUseCase,
      OperatorContextResolver operators) {
    this.importUseCase = importUseCase;
    this.listUseCase = listUseCase;
    this.operators = operators;
  }

  @PostMapping
  public ResponseEntity<SigmaImportResponse> importSigma(
      @Valid @RequestBody SigmaImportRequest request) {
    var cmd = new ImportCommand(TenantId.of(request.tenantId()), request.yaml(),
        request.overwriteByTitle());
    ImportResult result = importUseCase.importYaml(cmd, operators.currentOperator());
    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
  }

  @GetMapping
  public ResponseEntity<List<SigmaRuleResponse>> list(@RequestParam String tenantId) {
    var rules =
        listUseCase.listByTenant(TenantId.of(tenantId), operators.currentOperator()).stream()
            .map(SigmaRuleResponse::from)
            .toList();
    return ResponseEntity.ok(rules);
  }

  private static SigmaImportResponse toResponse(ImportResult result) {
    var summaries =
        result.createdRules().stream()
            .map(
                r -> {
                  // sigma 와 alert_rule 은 같은 index 위치에서 1:1.
                  var idx = result.createdRules().indexOf(r);
                  var sigma =
                      idx < result.importedSigma().size() ? result.importedSigma().get(idx) : null;
                  return new SigmaImportResponse.RuleSummary(
                      r.ruleId(),
                      sigma == null ? null : sigma.id(),
                      r.name(),
                      sigma == null ? null : sigma.level());
                })
            .toList();
    var notes =
        result.mappingNotes().stream()
            .map(n -> new SigmaImportResponse.MappingNote(n.alertRuleId(), n.sigmaRuleId(), n.unsupported()))
            .toList();
    return new SigmaImportResponse(result.createdRules().size(), summaries, notes);
  }
}
