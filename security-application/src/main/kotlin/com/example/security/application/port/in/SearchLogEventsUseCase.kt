package com.example.security.application.port.`in`

import com.example.security.application.query.SearchQuery
import com.example.security.application.query.SearchResult

/**
 * use case 2 — OpenSearch 에서 LogEvent 검색.
 *
 * tenantId 는 query 객체에 강제로 들어있으며, application layer 가 OpenSearch query 빌드 시
 * filter clause 로 다시 한 번 강제 주입한다 (사용자가 우회 불가).
 *
 * 검색 자체가 audit_entries 에 기록된다 (ISMS-P 2.9 통제).
 */
interface SearchLogEventsUseCase {

    fun search(query: SearchQuery, operator: OperatorContext): SearchResult
}
