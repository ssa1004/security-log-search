-- Sigma 룰 import 테이블.
--
-- Sigma 는 SigmaHQ 가 정의한 vendor 중립 SIEM 룰 표준이다 (https://github.com/SigmaHQ/sigma).
-- 외부 위협 인텔리전스의 룰을 우리 alert_rules 로 변환하면서, 원본 YAML / 메타데이터를 함께
-- 보관하여 재변환 / 감사 / 운영자 검토에 사용한다.
--
-- (sigma_id, tenant_id) 가 PK — 같은 sigma 룰이 여러 tenant 에 import 될 수 있다.
-- alert_rule_id 는 변환 결과로 생성된 alert_rules 의 row 와 1:1.

CREATE TABLE sigma_rules (
    sigma_id            VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(32)  NOT NULL,
    alert_rule_id       UUID         NOT NULL,
    title               VARCHAR(200) NOT NULL,
    level               VARCHAR(32),
    status              VARCHAR(32),
    author              VARCHAR(200),
    logsource_category  VARCHAR(64),
    logsource_product   VARCHAR(64),
    description         VARCHAR(2000),
    references_csv      VARCHAR(2000),
    tags_csv            VARCHAR(1000),
    source_yaml         TEXT         NOT NULL,
    imported_at         TIMESTAMP    NOT NULL,
    PRIMARY KEY (sigma_id, tenant_id),
    CONSTRAINT fk_sigma_rules_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id),
    CONSTRAINT fk_sigma_rules_alert  FOREIGN KEY (alert_rule_id) REFERENCES alert_rules(rule_id)
);

CREATE INDEX ix_sigma_rules_tenant ON sigma_rules (tenant_id);
CREATE INDEX ix_sigma_rules_level ON sigma_rules (level);
