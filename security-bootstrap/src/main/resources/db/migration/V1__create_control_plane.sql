-- 멀티테넌트 control plane — Postgres / H2 공통.

CREATE TABLE tenants (
    tenant_id           VARCHAR(32) PRIMARY KEY,
    display_name        VARCHAR(200) NOT NULL,
    retention_days      INTEGER NOT NULL,
    hot_retention_days  INTEGER NOT NULL,
    pii_policy          VARCHAR(16) NOT NULL,
    onboarded_at        TIMESTAMP NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE alert_rules (
    rule_id             UUID PRIMARY KEY,
    tenant_id           VARCHAR(32) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    description         VARCHAR(1000),
    type                VARCHAR(16) NOT NULL,
    filter_category     VARCHAR(64),
    filter_action       VARCHAR(64),
    filter_outcome      VARCHAR(16),
    group_by_field      VARCHAR(64) NOT NULL,
    threshold           INTEGER NOT NULL,
    window_seconds      BIGINT NOT NULL,
    severity            VARCHAR(16) NOT NULL,
    enabled             BOOLEAN NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    CONSTRAINT fk_alert_rules_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)
);

CREATE INDEX ix_alert_rules_tenant_enabled ON alert_rules (tenant_id, enabled);

CREATE TABLE alerts (
    alert_id              UUID PRIMARY KEY,
    tenant_id             VARCHAR(32) NOT NULL,
    rule_id               UUID NOT NULL,
    rule_name             VARCHAR(200) NOT NULL,
    severity              VARCHAR(16) NOT NULL,
    group_key             VARCHAR(256) NOT NULL,
    group_by_field        VARCHAR(64) NOT NULL,
    matched_count         INTEGER NOT NULL,
    window_start          TIMESTAMP NOT NULL,
    window_end            TIMESTAMP NOT NULL,
    fired_at              TIMESTAMP NOT NULL,
    status                VARCHAR(24) NOT NULL,
    triggering_event_ids  VARCHAR(4000),
    message               VARCHAR(1000),
    CONSTRAINT fk_alerts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)
);

CREATE INDEX ix_alerts_tenant_fired ON alerts (tenant_id, fired_at);
CREATE INDEX ix_alerts_status ON alerts (status);

-- audit_entries — append-only.
-- Postgres: CREATE TABLE 후 별도 trigger / role 으로 UPDATE/DELETE 차단 (운영 추가 작업).
CREATE TABLE audit_entries (
    entry_id     UUID PRIMARY KEY,
    tenant_id    VARCHAR(32) NOT NULL,
    occurred_at  TIMESTAMP NOT NULL,
    actor        VARCHAR(200) NOT NULL,
    actor_role   VARCHAR(200),
    action       VARCHAR(32) NOT NULL,
    target_type  VARCHAR(64),
    target_id    VARCHAR(256),
    source_ip    VARCHAR(64),
    details      VARCHAR(4000)
);

CREATE INDEX ix_audit_tenant_occurred ON audit_entries (tenant_id, occurred_at);
CREATE INDEX ix_audit_actor ON audit_entries (actor);
CREATE INDEX ix_audit_action ON audit_entries (action);

-- idempotency_keys — (tenant_id, key) 복합 PK.
CREATE TABLE idempotency_keys (
    tenant_id   VARCHAR(32) NOT NULL,
    idem_key    VARCHAR(200) NOT NULL,
    event_id    UUID NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    PRIMARY KEY (tenant_id, idem_key)
);

CREATE INDEX ix_idem_created_at ON idempotency_keys (created_at);
