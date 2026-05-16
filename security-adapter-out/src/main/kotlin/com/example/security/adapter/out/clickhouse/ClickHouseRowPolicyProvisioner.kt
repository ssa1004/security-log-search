package com.example.security.adapter.out.clickhouse

import com.example.security.domain.tenant.Tenant
import java.sql.SQLException
import javax.sql.DataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * ClickHouse Row Policy provisioning — tenant onboarding 시 호출.
 *
 * Row Policy 는 ClickHouse 의 row-level access control 메커니즘이다. 사용자가 어떤 query 를
 * 보내든 `WHERE tenant_id = currentSetting('tenant_id')` 가 강제로 AND 결합되어 다른
 * tenant 의 행은 절대 보이지 않는다.
 *
 * 구현 SQL (예시):
 * ```
 * CREATE ROW POLICY IF NOT EXISTS rp_tenant_acme
 *   ON events_raw, events_5m_mv, events_1h_mv
 *   USING tenant_id = currentSetting('tenant_id')
 *   TO sec_search_role;
 * ```
 */
@Component
@ConditionalOnProperty(
    name = ["security.clickhouse.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class ClickHouseRowPolicyProvisioner(
    @Qualifier("clickHouseDataSource") private val dataSource: DataSource,
) {

    fun provision(tenant: Tenant) {
        val policyName = "rp_tenant_" + tenant.tenantId.value.replace('-', '_')
        val tables = TARGET_TABLES.joinToString(", ")
        val sql = "CREATE ROW POLICY IF NOT EXISTS $policyName ON $tables " +
            "USING tenant_id = currentSetting('tenant_id') TO $ROLE"
        try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { st ->
                    st.execute(sql)
                    log.info(
                        "ClickHouse row policy applied: tenant={} policy={}",
                        tenant.tenantId, policyName,
                    )
                }
            }
        } catch (e: SQLException) {
            // tenant onboarding 자체는 실패시키지 않는다 — 운영자가 수동 적용 가능.
            log.warn(
                "ClickHouse row policy 적용 실패 (수동 적용 필요): tenant={} policy={} err={}",
                tenant.tenantId, policyName, e.message,
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ClickHouseRowPolicyProvisioner::class.java)
        private val TARGET_TABLES = arrayOf("events_raw", "events_5m_mv", "events_1h_mv")
        private const val ROLE = "sec_search_role"
    }
}
