package com.example.security.domain.event;

import com.example.security.domain.common.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 정규화 전 raw event — REST 또는 Kafka 로 들어온 그대로의 형태.
 *
 * <p>{@code source} 필드로 어떤 종류의 source 인지 (firewall / edr / syslog / app) 를 식별하고,
 * {@link com.example.security.domain.mapping.EventNormalizer} 가 적절한 매퍼를 골라
 * {@link LogEvent} 로 변환한다.
 *
 * @param source raw event 의 source 종류 (firewall / edr / syslog / app / aws-cloudtrail 등)
 * @param schema raw event 의 schema 힌트 (ecs / ocsf / vendor-{name})
 * @param payload key-value 형태의 raw 데이터
 */
public record RawEvent(
    TenantId tenantId,
    Instant receivedAt,
    String source,
    String schema,
    Map<String, Object> payload) {

  public RawEvent {
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(receivedAt, "receivedAt");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(payload, "payload");
    payload = Map.copyOf(payload);
  }
}
