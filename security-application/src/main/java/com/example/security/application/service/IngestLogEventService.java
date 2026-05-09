package com.example.security.application.service;

import com.example.security.application.port.in.IngestLogEventUseCase;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.application.port.out.EventPublisherPort;
import com.example.security.application.port.out.IdempotencyPort;
import com.example.security.application.port.out.TenantRepository;
import com.example.security.application.exception.TenantNotFoundException;
import com.example.security.domain.audit.AuditEntry;
import com.example.security.domain.event.RawEvent;
import com.example.security.domain.mapping.EventNormalizer;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * use case 1 — raw event 정규화 + Kafka 발행 + idempotency 보장.
 *
 * <p>처리 순서:
 *
 * <ol>
 *   <li>tenant 존재 검증
 *   <li>idempotency-key 가 있으면 lookup → 이미 본 키면 기존 eventId 반환 (duplicate=true)
 *   <li>raw → ECS / OCSF 정규화
 *   <li>idempotency tryClaim → 동시 다른 요청이 같은 키로 들어왔을 가능성 차단
 *   <li>events.normalized 발행
 * </ol>
 */
@Service
public class IngestLogEventService implements IngestLogEventUseCase {

  private final EventNormalizer normalizer;
  private final EventPublisherPort publisher;
  private final IdempotencyPort idempotency;
  private final TenantRepository tenants;
  private final AuditLogPort audit;
  private final Clock clock;

  public IngestLogEventService(
      EventNormalizer normalizer,
      EventPublisherPort publisher,
      IdempotencyPort idempotency,
      TenantRepository tenants,
      AuditLogPort audit,
      Clock clock) {
    this.normalizer = normalizer;
    this.publisher = publisher;
    this.idempotency = idempotency;
    this.tenants = tenants;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  public IngestResult ingest(RawEvent raw, String idempotencyKey) {
    var tenant =
        tenants
            .findById(raw.tenantId())
            .orElseThrow(() -> new TenantNotFoundException(raw.tenantId()));
    if (!tenant.active()) {
      throw new TenantNotFoundException(raw.tenantId());
    }

    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      var existing = idempotency.lookup(raw.tenantId(), idempotencyKey);
      if (existing.isPresent()) {
        return new IngestResult(existing.get(), true);
      }
    }

    var event = normalizer.normalize(raw);

    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      var claimed = idempotency.tryClaim(raw.tenantId(), idempotencyKey, event.eventId());
      if (!claimed) {
        // 다른 요청이 동일 키로 동시에 들어왔음 — lookup 으로 그쪽 결과 반환.
        var existing = idempotency.lookup(raw.tenantId(), idempotencyKey);
        if (existing.isPresent()) {
          return new IngestResult(existing.get(), true);
        }
      }
    }

    publisher.publish(event);

    // 감사 로그는 빈도가 너무 높아 INGEST 자체는 audit 에 안 남김 — 대량 트래픽이라 noise.
    // 단 Tenant 비활성화 / 정규화 실패 같은 예외만 audit 가 잡는다.
    return new IngestResult(event.eventId(), false);
  }

  /** 운영자 도구용 — INGEST 자체는 audit 안 남기지만, 디버그용 명시 호출 시에는 남길 수 있게. */
  public void recordIngestAudit(RawEvent raw, UUID eventId, String actor) {
    audit.append(
        new AuditEntry(
            UUID.randomUUID(),
            raw.tenantId(),
            clock.instant(),
            actor,
            "system",
            AuditEntry.AuditAction.SEARCH, // dummy — INGEST audit 은 별도 action 추가 시 교체
            "raw_event",
            eventId.toString(),
            null,
            Map.of("source", raw.source(), "schema", raw.schema())));
  }
}
