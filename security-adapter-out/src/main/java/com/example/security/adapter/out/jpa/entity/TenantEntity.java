package com.example.security.adapter.out.jpa.entity;

import com.example.security.domain.common.TenantId;
import com.example.security.domain.tenant.Tenant;
import com.example.security.domain.tenant.Tenant.PiiMaskingPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/** tenants 테이블. */
@Entity
@Table(name = "tenants")
public class TenantEntity {

  @Id
  @Column(name = "tenant_id", nullable = false, length = 32)
  private String tenantId;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "retention_days", nullable = false)
  private int retentionDays;

  @Column(name = "hot_retention_days", nullable = false)
  private int hotRetentionDays;

  @Enumerated(EnumType.STRING)
  @Column(name = "pii_policy", nullable = false, length = 16)
  private PiiMaskingPolicy piiPolicy;

  @Column(name = "onboarded_at", nullable = false)
  private Instant onboardedAt;

  @Column(name = "active", nullable = false)
  private boolean active;

  protected TenantEntity() {}

  public static TenantEntity from(Tenant tenant) {
    var e = new TenantEntity();
    e.tenantId = tenant.tenantId().value();
    e.displayName = tenant.displayName();
    e.retentionDays = (int) tenant.retention().toDays();
    e.hotRetentionDays = (int) tenant.hotRetention().toDays();
    e.piiPolicy = tenant.piiPolicy();
    e.onboardedAt = tenant.onboardedAt();
    e.active = tenant.active();
    return e;
  }

  public Tenant toDomain() {
    return new Tenant(
        TenantId.of(tenantId),
        displayName,
        Duration.ofDays(retentionDays),
        Duration.ofDays(hotRetentionDays),
        piiPolicy,
        onboardedAt,
        active);
  }

  public String getTenantId() {
    return tenantId;
  }
}
