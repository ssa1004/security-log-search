package com.example.security.application.service

import com.example.security.application.exception.TenantMismatchException
import com.example.security.application.port.`in`.ImportSigmaRuleUseCase
import com.example.security.application.port.`in`.ListImportedSigmaRulesUseCase
import com.example.security.application.port.`in`.OperatorContext
import com.example.security.application.port.out.AlertRuleRepository
import com.example.security.application.port.out.AuditLogPort
import com.example.security.application.port.out.SigmaRuleRepository
import com.example.security.application.sigma.SigmaYamlParser
import com.example.security.domain.audit.AuditEntry
import com.example.security.domain.common.TenantId
import com.example.security.domain.rule.AlertRule
import com.example.security.domain.sigma.SigmaRule
import com.example.security.domain.sigma.SigmaToAlertRuleMapper
import java.time.Clock
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * use case 9, 10 — Sigma 룰 import / 조회.
 *
 * 처리 순서 (importYaml):
 *
 *  1. operator → tenant 권한 검증
 *  2. YAML parse (SnakeYAML SafeConstructor)
 *  3. 각 SigmaRule → AlertRule 변환 (`SigmaToAlertRuleMapper`)
 *  4. 기존 sigma_id 가 있으면 update 모드로 (overwriteByTitle 옵션) 또는 skip
 *  5. AlertRule 저장 + Sigma 원본 저장 + audit
 */
@Service
open class ImportSigmaRuleService(
    private val sigmaRepo: SigmaRuleRepository,
    private val alertRuleRepo: AlertRuleRepository,
    private val parser: SigmaYamlParser,
    private val mapper: SigmaToAlertRuleMapper,
    private val audit: AuditLogPort,
    private val clock: Clock,
) : ImportSigmaRuleUseCase, ListImportedSigmaRulesUseCase {

    @Transactional
    override fun importYaml(
        command: ImportSigmaRuleUseCase.ImportCommand,
        operator: OperatorContext,
    ): ImportSigmaRuleUseCase.ImportResult {
        enforceTenant(operator, command.tenantId)
        val sigmaList = parser.parseAll(command.yaml)
        if (sigmaList.isEmpty()) {
            return ImportSigmaRuleUseCase.ImportResult(emptyList(), emptyList(), emptyList())
        }

        val createdRules = ArrayList<AlertRule>(sigmaList.size)
        val notes = ArrayList<ImportSigmaRuleUseCase.MappingNote>(sigmaList.size)
        val savedSigma = ArrayList<SigmaRule>(sigmaList.size)

        for (sigma in sigmaList) {
            val existingRuleId = sigmaRepo.findAlertRuleId(sigma.id, command.tenantId)
            if (existingRuleId.isPresent && !command.overwriteByTitle) {
                log.info("Sigma 룰 {} 는 이미 import 됨, skip", sigma.id)
                continue
            }

            val mapping = mapper.map(sigma, command.tenantId, clock.instant())
            val rule: AlertRule = if (existingRuleId.isPresent) {
                // 기존 alert_rule 의 id 를 유지하고 내용을 덮어씀.
                val withSameId = AlertRule(
                    existingRuleId.get(),
                    mapping.rule.tenantId,
                    mapping.rule.name,
                    mapping.rule.description,
                    mapping.rule.type,
                    mapping.rule.filterCategory,
                    mapping.rule.filterAction,
                    mapping.rule.filterOutcome,
                    mapping.rule.groupByField,
                    mapping.rule.threshold,
                    mapping.rule.window,
                    mapping.rule.severity,
                    mapping.rule.enabled,
                    mapping.rule.createdAt,
                    clock.instant(),
                )
                alertRuleRepo.save(withSameId)
            } else {
                alertRuleRepo.save(mapping.rule)
            }
            val saved = sigmaRepo.save(sigma, command.tenantId, rule.ruleId)

            createdRules.add(rule)
            savedSigma.add(saved)
            notes.add(ImportSigmaRuleUseCase.MappingNote(rule.ruleId, sigma.id, mapping.unsupported))

            audit.append(
                AuditEntry(
                    UUID.randomUUID(),
                    command.tenantId,
                    clock.instant(),
                    operator.subject,
                    operator.roles.joinToString(",") { it.name },
                    AuditEntry.AuditAction.RULE_CREATED,
                    "sigma_rule",
                    sigma.id,
                    operator.sourceIp,
                    mapOf(
                        "title" to sigma.title,
                        "level" to (sigma.level ?: ""),
                        "alert_rule_id" to rule.ruleId.toString(),
                        "unsupported_count" to mapping.unsupported.size.toString(),
                    ),
                )
            )
        }

        return ImportSigmaRuleUseCase.ImportResult(
            java.util.List.copyOf(createdRules),
            java.util.List.copyOf(savedSigma),
            java.util.List.copyOf(notes),
        )
    }

    @Transactional(readOnly = true)
    override fun listByTenant(tenantId: TenantId, operator: OperatorContext): List<SigmaRule> {
        enforceTenant(operator, tenantId)
        return sigmaRepo.findByTenant(tenantId)
    }

    private fun enforceTenant(operator: OperatorContext, tenant: TenantId) {
        if (!operator.canQueryOtherTenant() && operator.tenantId != tenant) {
            throw TenantMismatchException(operator.tenantId, tenant)
        }
        CrossTenantAccessAudit.recordIfCrossTenant(
            audit, clock, operator, tenant, "sigma_rule", tenant.value,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ImportSigmaRuleService::class.java)
    }
}
