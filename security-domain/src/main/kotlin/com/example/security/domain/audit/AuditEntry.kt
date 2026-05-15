package com.example.security.domain.audit

import com.example.security.domain.common.TenantId
import java.time.Instant
import java.util.UUID

/**
 * 감사 기록 — append-only.
 *
 * ISMS-P 2.9 (감사) 통제 항목의 핵심 자료. 누가 (actor) 언제 (occurredAt) 어떤 자원에
 * (target) 어떤 동작 (action) 을 했는지를 변조 불가능하게 기록한다.
 *
 * 본 도메인에서 감사 대상이 되는 동작:
 * - INGEST — raw event 수집 (대량 트래픽이라 평소엔 기록 안 하고, 운영자 디버그 호출 시에만)
 * - SEARCH — 운영자가 검색 API 호출 (검색어 + tenant + 결과 카운트)
 * - RULE_CREATED / RULE_UPDATED / RULE_DELETED — 알람 룰 변경
 * - ALERT_FIRED — Flink job 이 룰 매칭으로 알람 발화 (시스템 동작)
 * - ALERT_ACKNOWLEDGED / ALERT_RESOLVED / ALERT_FALSE_POSITIVE — 알람 처리
 * - INDEX_ROLLOVER / ALIAS_SWAP — admin 인덱스 작업
 * - TENANT_ONBOARDED / TENANT_DEACTIVATED — 테넌트 라이프사이클
 * - CROSS_TENANT_ACCESS — 플랫폼 관리자가 본인 외 tenant 데이터에 접근 (ISMS-P 2.6)
 *
 * `details` 는 호출 측이 불변 맵 (`Map.of(...)` 등) 으로 넘기는 것을 전제로 한다.
 */
@JvmRecord
data class AuditEntry(
    val entryId: UUID,
    val tenantId: TenantId,
    val occurredAt: Instant,
    val actor: String,
    val actorRole: String?,
    val action: AuditAction,
    val targetType: String?,
    val targetId: String?,
    val sourceIp: String?,
    val details: Map<String, String>,
) {

    enum class AuditAction {
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
        EXPORT_RESULTS,
    }
}
