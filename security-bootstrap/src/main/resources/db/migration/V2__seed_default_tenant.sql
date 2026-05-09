-- local / dev 용 default tenant 시드.
INSERT INTO tenants (tenant_id, display_name, retention_days, hot_retention_days, pii_policy, onboarded_at, active)
VALUES ('acme', 'Acme Corporation', 365, 7, 'IP_ONLY', CURRENT_TIMESTAMP, TRUE);
