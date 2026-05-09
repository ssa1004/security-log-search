package com.example.security.application.port.in;

import com.example.security.domain.event.RawEvent;
import java.util.UUID;

/**
 * use case 1 — raw event 를 받아 ECS / OCSF 정규화 후 Kafka {@code events.normalized} topic 으로 발행.
 *
 * <p>idempotency-key 가 동일한 요청은 한 번만 처리한다 (Postgres idempotency_keys 테이블).
 */
public interface IngestLogEventUseCase {

  IngestResult ingest(RawEvent raw, String idempotencyKey);

  /** 처리 결과. */
  record IngestResult(UUID eventId, boolean duplicate) {}
}
