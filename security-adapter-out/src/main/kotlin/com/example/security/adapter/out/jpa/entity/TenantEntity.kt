package com.example.security.adapter.out.jpa.entity

import com.example.security.domain.common.TenantId
import com.example.security.domain.tenant.Tenant
import com.example.security.domain.tenant.Tenant.PiiMaskingPolicy
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Duration
import java.time.Instant

/**
 * tenants 테이블.
 *
 * JPA Kotlin plugin (plugin.jpa) 가 no-arg constructor 를 자동 합성한다. private setter 로
 * 외부에서 mutate 못 하게 막되, JPA 가 reflection 으로 채울 수 있게 var 로 둔다.
 */
@Entity
@Table(name = "tenants")
class TenantEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 32)
    @get:JvmName("getTenantId")
    var tenantId: String = ""
        private set

    @Column(name = "display_name", nullable = false)
    private var displayName: String = ""

    @Column(name = "retention_days", nullable = false)
    private var retentionDays: Int = 0

    @Column(name = "hot_retention_days", nullable = false)
    private var hotRetentionDays: Int = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "pii_policy", nullable = false, length = 16)
    private var piiPolicy: PiiMaskingPolicy = PiiMaskingPolicy.NONE

    @Column(name = "onboarded_at", nullable = false)
    private var onboardedAt: Instant = Instant.EPOCH

    @Column(name = "active", nullable = false)
    private var active: Boolean = false

    fun toDomain(): Tenant =
        Tenant(
            TenantId.of(tenantId),
            displayName,
            Duration.ofDays(retentionDays.toLong()),
            Duration.ofDays(hotRetentionDays.toLong()),
            piiPolicy,
            onboardedAt,
            active,
        )

    companion object {
        @JvmStatic
        fun from(tenant: Tenant): TenantEntity = TenantEntity().apply {
            tenantId = tenant.tenantId.value
            displayName = tenant.displayName
            retentionDays = tenant.retention.toDays().toInt()
            hotRetentionDays = tenant.hotRetention.toDays().toInt()
            piiPolicy = tenant.piiPolicy
            onboardedAt = tenant.onboardedAt
            active = tenant.active
        }
    }
}
