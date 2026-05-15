package com.example.security.domain.mapping

import com.example.security.domain.event.LogEvent
import com.example.security.domain.event.RawEvent

/**
 * raw event 를 정규화된 [LogEvent] (ECS 형태) 로 변환하는 도메인 서비스.
 *
 * 구현체는 schema (ecs / ocsf / vendor-*) 별로 분리되며 본 인터페이스가 라우팅한다.
 *
 * 구현체가 던질 수 있는 예외:
 * - [UnsupportedSchemaException] — schema 힌트에 해당하는 매퍼가 없음
 * - [IllegalArgumentException] — payload 의 필수 필드가 비어있음
 */
interface EventNormalizer {

    fun normalize(raw: RawEvent): LogEvent

    class UnsupportedSchemaException(schema: String) :
        RuntimeException("지원하지 않는 schema: $schema")
}
