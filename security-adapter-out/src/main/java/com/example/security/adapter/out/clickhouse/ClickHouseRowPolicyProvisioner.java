package com.example.security.adapter.out.clickhouse;

import com.example.security.domain.tenant.Tenant;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ClickHouse Row Policy provisioning — tenant onboarding 시 호출.
 *
 * <p>Row Policy 는 ClickHouse 의 row-level access control 메커니즘이다. 사용자가 어떤 query 를
 * 보내든 {@code WHERE tenant_id = currentSetting('tenant_id')} 가 강제로 AND 결합되어 다른
 * tenant 의 행은 절대 보이지 않는다.
 *
 * <p>구현 SQL (예시):
 *
 * <pre>
 * CREATE ROW POLICY IF NOT EXISTS rp_tenant_acme
 *   ON events_raw, events_5m_mv, events_1h_mv
 *   USING tenant_id = currentSetting('tenant_id')
 *   TO sec_search_role;
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "security.clickhouse.enabled", havingValue = "true", matchIfMissing = false)
public class ClickHouseRowPolicyProvisioner {

  private static final Logger log = LoggerFactory.getLogger(ClickHouseRowPolicyProvisioner.class);
  private static final String[] TARGET_TABLES = {"events_raw", "events_5m_mv", "events_1h_mv"};
  private static final String ROLE = "sec_search_role";

  private final DataSource dataSource;

  public ClickHouseRowPolicyProvisioner(@Qualifier("clickHouseDataSource") DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public void provision(Tenant tenant) {
    var policyName = "rp_tenant_" + tenant.tenantId().value().replace('-', '_');
    var tables = String.join(", ", TARGET_TABLES);
    var sql =
        "CREATE ROW POLICY IF NOT EXISTS "
            + policyName
            + " ON "
            + tables
            + " USING tenant_id = currentSetting('tenant_id') TO "
            + ROLE;
    try (var conn = dataSource.getConnection();
        var st = conn.createStatement()) {
      st.execute(sql);
      log.info("ClickHouse row policy applied: tenant={} policy={}", tenant.tenantId(), policyName);
    } catch (SQLException e) {
      // tenant onboarding 자체는 실패시키지 않는다 — 운영자가 수동 적용 가능.
      log.warn(
          "ClickHouse row policy 적용 실패 (수동 적용 필요): tenant={} policy={} err={}",
          tenant.tenantId(),
          policyName,
          e.getMessage());
    }
  }
}
