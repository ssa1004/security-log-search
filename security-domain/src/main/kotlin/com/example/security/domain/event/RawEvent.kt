package com.example.security.domain.event

import com.example.security.domain.common.TenantId
import java.time.Instant
import java.util.Objects

/**
 * 정규화 전 raw event — REST 또는 Kafka 로 들어온 그대로의 형태.
 *
 * `source` 필드로 어떤 종류의 source 인지 (firewall / edr / syslog / app) 를 식별하고,
 * [com.example.security.domain.mapping.EventNormalizer] 가 적절한 매퍼를 골라
 * [LogEvent] 로 변환한다.
 *
 * `payload` 는 생성 시 불변 복사본으로 보관한다 — 호출 측이 이후 원본 맵을 바꿔도 영향 없음.
 * Java record 와 동일한 호출부 호환을 위해 accessor 이름은 `tenantId()` / `payload()` 형태를
 * 유지한다 (일반 class + custom equals/hashCode — 방어적 복사 보존).
 */
class RawEvent(
    tenantId: TenantId,
    receivedAt: Instant,
    source: String,
    schema: String,
    payload: Map<String, Any>,
) {
    @get:JvmName("tenantId")
    val tenantId: TenantId = tenantId

    @get:JvmName("receivedAt")
    val receivedAt: Instant = receivedAt

    /** raw event 의 source 종류 (firewall / edr / syslog / app / aws-cloudtrail 등). */
    @get:JvmName("source")
    val source: String = source

    /** raw event 의 schema 힌트 (ecs / ocsf / vendor-{name}). */
    @get:JvmName("schema")
    val schema: String = schema

    /** key-value 형태의 raw 데이터 — 불변 복사본. */
    @get:JvmName("payload")
    val payload: Map<String, Any> = java.util.Map.copyOf(payload)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawEvent) return false
        return tenantId == other.tenantId &&
            receivedAt == other.receivedAt &&
            source == other.source &&
            schema == other.schema &&
            payload == other.payload
    }

    override fun hashCode(): Int =
        Objects.hash(tenantId, receivedAt, source, schema, payload)

    override fun toString(): String =
        "RawEvent[tenantId=$tenantId, receivedAt=$receivedAt, source=$source, " +
            "schema=$schema, payload=$payload]"
}
