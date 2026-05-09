package com.example.security.adapter.out.jpa.entity;

import com.example.security.domain.sigma.SigmaRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * sigma_rules 테이블 — import 한 Sigma 룰 원본 + 변환 결과 alert_rule_id.
 *
 * <p>{@code (sigma_id, tenant_id)} 가 PK — 같은 sigma 가 다른 tenant 에 import 될 수 있다.
 *
 * <p>원본 YAML 은 {@code source} 에 그대로 보관 (운영자가 변환 결과를 의심할 때 원본 비교용).
 */
@Entity
@Table(
    name = "sigma_rules",
    indexes = {@Index(name = "ix_sigma_tenant", columnList = "tenant_id")})
@IdClass(SigmaRuleEntity.PK.class)
public class SigmaRuleEntity {

  @Id
  @Column(name = "sigma_id", nullable = false, length = 64)
  private String sigmaId;

  @Id
  @Column(name = "tenant_id", nullable = false, length = 32)
  private String tenantId;

  @Column(name = "alert_rule_id", nullable = false)
  private UUID alertRuleId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 32)
  private String level;

  @Column(length = 32)
  private String status;

  @Column(length = 200)
  private String author;

  /** {@code logsource.category}. */
  @Column(name = "logsource_category", length = 64)
  private String logsourceCategory;

  /** {@code logsource.product}. */
  @Column(name = "logsource_product", length = 64)
  private String logsourceProduct;

  /** Sigma {@code description} (1차). */
  @Column(length = 2000)
  private String description;

  /** {@code references} 를 콤마 구분. */
  @Column(name = "references_csv", length = 2000)
  private String referencesCsv;

  /** {@code tags} (MITRE ATT&CK 등) 콤마 구분. */
  @Column(name = "tags_csv", length = 1000)
  private String tagsCsv;

  /** Sigma YAML 원본 — 재변환 / 감사용. Postgres TEXT, H2(PostgreSQL mode) CLOB-equivalent. */
  @Column(name = "source_yaml", nullable = false, columnDefinition = "TEXT")
  private String sourceYaml;

  @Column(name = "imported_at", nullable = false)
  private Instant importedAt;

  protected SigmaRuleEntity() {}

  public static SigmaRuleEntity from(SigmaRule sigma, String tenantIdValue, UUID alertRuleId) {
    var e = new SigmaRuleEntity();
    e.sigmaId = sigma.id();
    e.tenantId = tenantIdValue;
    e.alertRuleId = alertRuleId;
    e.title = trim(sigma.title(), 200);
    e.level = trim(sigma.level(), 32);
    e.status = trim(sigma.status(), 32);
    e.author = trim(sigma.author(), 200);
    e.logsourceCategory = trim(sigma.logsource().get("category"), 64);
    e.logsourceProduct = trim(sigma.logsource().get("product"), 64);
    e.description = trim(sigma.description(), 2000);
    e.referencesCsv = trim(String.join(",", sigma.references()), 2000);
    e.tagsCsv = trim(String.join(",", sigma.tags()), 1000);
    e.sourceYaml = sigma.source();
    e.importedAt = sigma.importedAt();
    return e;
  }

  /** entity → domain 복원. detection 본문은 원본 YAML 에서 다시 파싱해야 함. */
  public SigmaRule toDomain(java.util.function.Function<String, SigmaRule> reparser) {
    return reparser.apply(sourceYaml);
  }

  public UUID getAlertRuleId() {
    return alertRuleId;
  }

  public String getSigmaId() {
    return sigmaId;
  }

  public String getTenantId() {
    return tenantId;
  }

  private static String trim(String v, int max) {
    if (v == null) return null;
    var trimmed = v.trim();
    if (trimmed.isEmpty()) return null;
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }

  /** 복합 PK — sigma_id + tenant_id. */
  public static class PK implements Serializable {

    private String sigmaId;
    private String tenantId;

    public PK() {}

    public PK(String sigmaId, String tenantId) {
      this.sigmaId = sigmaId;
      this.tenantId = tenantId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PK pk)) return false;
      return java.util.Objects.equals(sigmaId, pk.sigmaId)
          && java.util.Objects.equals(tenantId, pk.tenantId);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(sigmaId, tenantId);
    }
  }
}
