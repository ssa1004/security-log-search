package com.example.security.adapter.out.jpa.entity

import com.example.security.domain.sigma.SigmaRule
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.Objects
import java.util.UUID
import java.util.function.Function

/**
 * sigma_rules 테이블 — import 한 Sigma 룰 원본 + 변환 결과 alert_rule_id.
 *
 * `(sigma_id, tenant_id)` 가 PK — 같은 sigma 가 다른 tenant 에 import 될 수 있다.
 *
 * 원본 YAML 은 `source` 에 그대로 보관 (운영자가 변환 결과를 의심할 때 원본 비교용).
 */
@Entity
@Table(
    name = "sigma_rules",
    indexes = [Index(name = "ix_sigma_tenant", columnList = "tenant_id")],
)
@IdClass(SigmaRuleEntity.PK::class)
class SigmaRuleEntity {

    @Id
    @Column(name = "sigma_id", nullable = false, length = 64)
    @get:JvmName("getSigmaId")
    var sigmaId: String = ""
        private set

    @Id
    @Column(name = "tenant_id", nullable = false, length = 32)
    @get:JvmName("getTenantId")
    var tenantId: String = ""
        private set

    @Column(name = "alert_rule_id", nullable = false)
    @get:JvmName("getAlertRuleId")
    var alertRuleId: UUID = UUID(0, 0)
        private set

    @Column(nullable = false, length = 200)
    private var title: String = ""

    @Column(length = 32)
    private var level: String? = null

    @Column(length = 32)
    private var status: String? = null

    @Column(length = 200)
    private var author: String? = null

    /** `logsource.category`. */
    @Column(name = "logsource_category", length = 64)
    private var logsourceCategory: String? = null

    /** `logsource.product`. */
    @Column(name = "logsource_product", length = 64)
    private var logsourceProduct: String? = null

    /** Sigma `description` (1차). */
    @Column(length = 2000)
    private var description: String? = null

    /** `references` 를 콤마 구분. */
    @Column(name = "references_csv", length = 2000)
    private var referencesCsv: String? = null

    /** `tags` (MITRE ATT&CK 등) 콤마 구분. */
    @Column(name = "tags_csv", length = 1000)
    private var tagsCsv: String? = null

    /** Sigma YAML 원본 — 재변환 / 감사용. Postgres TEXT, H2(PostgreSQL mode) CLOB-equivalent. */
    @Column(name = "source_yaml", nullable = false, columnDefinition = "TEXT")
    private var sourceYaml: String = ""

    @Column(name = "imported_at", nullable = false)
    private var importedAt: Instant = Instant.EPOCH

    /** entity → domain 복원. detection 본문은 원본 YAML 에서 다시 파싱해야 함. */
    fun toDomain(reparser: Function<String, SigmaRule>): SigmaRule = reparser.apply(sourceYaml)

    /** 복합 PK — sigma_id + tenant_id. */
    class PK : Serializable {
        var sigmaId: String? = null
        var tenantId: String? = null

        constructor()

        constructor(sigmaId: String, tenantId: String) {
            this.sigmaId = sigmaId
            this.tenantId = tenantId
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PK) return false
            return Objects.equals(sigmaId, other.sigmaId) && Objects.equals(tenantId, other.tenantId)
        }

        override fun hashCode(): Int = Objects.hash(sigmaId, tenantId)
    }

    companion object {
        @JvmStatic
        fun from(sigma: SigmaRule, tenantIdValue: String, alertRuleId: UUID): SigmaRuleEntity =
            SigmaRuleEntity().apply {
                sigmaId = sigma.id
                tenantId = tenantIdValue
                this.alertRuleId = alertRuleId
                title = trim(sigma.title, 200) ?: ""
                level = trim(sigma.level, 32)
                status = trim(sigma.status, 32)
                author = trim(sigma.author, 200)
                logsourceCategory = trim(sigma.logsource["category"], 64)
                logsourceProduct = trim(sigma.logsource["product"], 64)
                description = trim(sigma.description, 2000)
                referencesCsv = trim(sigma.references.joinToString(","), 2000)
                tagsCsv = trim(sigma.tags.joinToString(","), 1000)
                sourceYaml = sigma.source
                importedAt = sigma.importedAt
            }

        private fun trim(v: String?, max: Int): String? {
            if (v == null) return null
            val trimmed = v.trim()
            if (trimmed.isEmpty()) return null
            return if (trimmed.length <= max) trimmed else trimmed.substring(0, max)
        }
    }
}
