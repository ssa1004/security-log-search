package com.example.security.adapter.`in`.rest.controller

import com.example.security.adapter.`in`.rest.dto.SigmaImportRequest
import com.example.security.adapter.`in`.rest.dto.SigmaImportResponse
import com.example.security.adapter.`in`.rest.dto.SigmaRuleResponse
import com.example.security.adapter.`in`.security.OperatorContextResolver
import com.example.security.application.port.`in`.ImportSigmaRuleUseCase
import com.example.security.application.port.`in`.ImportSigmaRuleUseCase.ImportCommand
import com.example.security.application.port.`in`.ImportSigmaRuleUseCase.ImportResult
import com.example.security.application.port.`in`.ListImportedSigmaRulesUseCase
import com.example.security.domain.common.TenantId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * use case 9, 10 — Sigma 룰 import / 조회 endpoint.
 *
 *  - POST /api/v1/sigma-rules — YAML import → AlertRule 변환
 *  - GET  /api/v1/sigma-rules?tenantId=acme — import 한 Sigma 룰 메타데이터 목록
 */
@RestController
@RequestMapping("/api/v1/sigma-rules")
class SigmaRuleController(
    private val importUseCase: ImportSigmaRuleUseCase,
    private val listUseCase: ListImportedSigmaRulesUseCase,
    private val operators: OperatorContextResolver,
) {

    @PostMapping
    fun importSigma(@Valid @RequestBody request: SigmaImportRequest): ResponseEntity<SigmaImportResponse> {
        val cmd = ImportCommand(TenantId.of(request.tenantId), request.yaml, request.overwriteByTitle)
        val result = importUseCase.importYaml(cmd, operators.currentOperator())
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result))
    }

    @GetMapping
    fun list(@RequestParam tenantId: String): ResponseEntity<List<SigmaRuleResponse>> {
        val rules = listUseCase
            .listByTenant(TenantId.of(tenantId), operators.currentOperator())
            .map { SigmaRuleResponse.from(it) }
        return ResponseEntity.ok(rules)
    }

    private fun toResponse(result: ImportResult): SigmaImportResponse {
        val summaries = result.createdRules.mapIndexed { idx, r ->
            // sigma 와 alert_rule 은 같은 index 위치에서 1:1.
            val sigma = if (idx < result.importedSigma.size) result.importedSigma[idx] else null
            SigmaImportResponse.RuleSummary(
                r.ruleId,
                sigma?.id,
                r.name,
                sigma?.level,
            )
        }
        val notes = result.mappingNotes.map {
            SigmaImportResponse.MappingNote(it.alertRuleId, it.sigmaRuleId, it.unsupported)
        }
        return SigmaImportResponse(result.createdRules.size, summaries, notes)
    }
}
