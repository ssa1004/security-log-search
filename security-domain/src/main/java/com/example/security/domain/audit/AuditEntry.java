package com.example.security.domain.audit;

import com.example.security.domain.common.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 감사 기록 — append-only.
 *
 * <p>ISMS-P 2.9 (감사) 통제 항목의 핵심 자료. 누가 (actor) 언제 (occurredAt) 어떤 자원에
 * (target) 어떤 동작 (action) 을 했는지를 변조 불가능하게 기록한다.
 *
 * <p>본 도메인에서 감사 대상이 되는 동작:
 *
 * <ul>
 *   <li>INGEST — raw event 수집 (대량 트래픽이라 평소엔 기록 안 하고, 운영자 디버그 호출 시에만)
 *   <li>SEARCH — 운영자가 검색 API 호출 (검색어 + tenant + 결과 카운트)
 *   <li>RULE_CREATED / RULE_UPDATED / RULE_DELETED — 알람 룰 변경
 *   <li>ALERT_FIRED — Flink job 이 룰 매칭으로 알람 발화 (시스템 동작)
 *   <li>ALERT_ACKNOWLEDGED / ALERT_RESOLVED / ALERT_FALSE_POSITIVE — 알람 처리
 *   <li>INDEX_ROLLOVER / ALIAS_SWAP — admin 인덱스 작업
 *   <li>TENANT_ONBOARDED / TENANT_DEACTIVATED — 테넌트 라이프사이클
 *   <li>CROSS_TENANT_ACCESS — 플랫폼 관리자가 본인 외 tenant 데이터에 접근 (ISMS-P 2.6)
 * </ul>
 */
public record AuditEntry(
    UUID entryId,
    TenantId tenantId,
    Instant occurredAt,
    String actor,
    String actorRole,
    AuditAction action,
    String targetType,
    String targetId,
    String sourceIp,
    Map<String, String> details) {

  public AuditEntry {
    Objects.requireNonNull(entryId);
    Objects.requireNonNull(tenantId);
    Objects.requireNonNull(occurredAt);
    Objects.requireNonNull(actor);
    Objects.requireNonNull(action);
    details = details == null ? Map.of() : Map.copyOf(details);
  }

  public enum AuditAction {
    INGEST,
    SEARCH,
    STATS_QUERY,
    RULE_CREATED,
    RULE_UPDATED,
    RULE_DELETED,
    ALERT_FIRED,
    ALERT_ACKNOWLEDGED,
    ALERT_RESOLVED,
    ALERT_FALSE_POSITIVE,
    INDEX_CREATED,
    INDEX_ROLLOVER,
    ALIAS_SWAP,
    ILM_POLICY_APPLIED,
    TENANT_ONBOARDED,
    TENANT_DEACTIVATED,
    CROSS_TENANT_ACCESS,
    LOGIN_OPERATOR,
    EXPORT_RESULTS
  }
}
