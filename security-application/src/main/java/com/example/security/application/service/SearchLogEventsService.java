package com.example.security.application.service;

import com.example.security.application.exception.TenantMismatchException;
import com.example.security.application.exception.TenantNotFoundException;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.in.SearchLogEventsUseCase;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.application.port.out.EventSearchPort;
import com.example.security.application.port.out.TenantRepository;
import com.example.security.application.query.SearchQuery;
import com.example.security.application.query.SearchResult;
import com.example.security.domain.audit.AuditEntry;
import com.example.security.domain.audit.AuditEntry.AuditAction;
import com.example.security.domain.event.PiiMasker;
import com.example.security.domain.event.LogEvent;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * use case 2 — 검색.
 *
 * <p>다음을 모두 강제:
 *
 * <ul>
 *   <li>operator.tenantId == query.tenantId (admin 면 우회 가능)
 *   <li>tenantId filter 가 OpenSearch query 의 filter clause 에 항상 포함
 *   <li>결과는 tenant 의 PII 정책에 따라 마스킹
 *   <li>모든 검색은 audit_entries 에 기록
 * </ul>
 */
@Service
public class SearchLogEventsService implements SearchLogEventsUseCase {

  private final EventSearchPort searchPort;
  private final TenantRepository tenants;
  private final AuditLogPort audit;
  private final Clock clock;

  public SearchLogEventsService(
      EventSearchPort searchPort,
      TenantRepository tenants,
      AuditLogPort audit,
      Clock clock) {
    this.searchPort = searchPort;
    this.tenants = tenants;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  public SearchResult search(SearchQuery query, OperatorContext operator) {
    enforceTenant(operator, query.tenantId());
    CrossTenantAccessAudit.recordIfCrossTenant(
        audit, clock, operator, query.tenantId(), "search", query.luceneQueryString());

    var tenant =
        tenants.findById(query.tenantId()).orElseThrow(() -> new TenantNotFoundException(query.tenantId()));

    var raw = searchPort.search(query);

    var maskedHits =
        raw.hits().stream()
            .map(e -> PiiMasker.mask(e, tenant.piiPolicy()))
            .collect(Collectors.toList());

    var maskedResult =
        new SearchResult(maskedHits, raw.totalHits(), raw.facets(), raw.nextCursor());

    auditSearch(query, operator, maskedHits.size());

    return maskedResult;
  }

  private void enforceTenant(OperatorContext operator, com.example.security.domain.common.TenantId requested) {
    if (operator.canQueryOtherTenant()) return;
    if (!operator.tenantId().equals(requested)) {
      // 우회 시도 — audit 후 거부.
      audit.append(
          new AuditEntry(
              UUID.randomUUID(),
              operator.tenantId(),
              clock.instant(),
              operator.subject(),
              roleString(operator),
              AuditAction.SEARCH,
              "tenant_mismatch",
              requested.value(),
              operator.sourceIp(),
              Map.of("denied", "true", "requested_tenant", requested.value())));
      throw new TenantMismatchException(operator.tenantId(), requested);
    }
  }

  private void auditSearch(SearchQuery query, OperatorContext operator, int returned) {
    audit.append(
        new AuditEntry(
            UUID.randomUUID(),
            query.tenantId(),
            clock.instant(),
            operator.subject(),
            roleString(operator),
            AuditAction.SEARCH,
            "search",
            query.luceneQueryString(),
            operator.sourceIp(),
            Map.of(
                "query", query.luceneQueryString(),
                "filters", query.termFilters().toString(),
                "returned", Integer.toString(returned))));
  }

  private static String roleString(OperatorContext operator) {
    return operator.roles().stream().map(Enum::name).collect(Collectors.joining(","));
  }
}
