package com.example.security.domain.mapping

import com.example.security.domain.event.LogEvent
import com.example.security.domain.event.RawEvent
import com.example.security.domain.mapping.source.CloudTrailToEcsMapper
import com.example.security.domain.mapping.source.K8sAuditToEcsMapper
import java.util.Locale

/**
 * schema 힌트로 매퍼를 라우팅하는 컴포지트. application layer 에서 단일 entry point 로 사용.
 *
 * 등록 매퍼:
 * - `ecs` — [EcsNormalizer] (ECS 가 이미 정규화된 dotted notation 입력)
 * - `ocsf` — [OcsfNormalizer] (OCSF 표준 → ECS)
 * - `aws-cloudtrail` — [CloudTrailToEcsMapper]
 * - `k8s-audit` — [K8sAuditToEcsMapper]
 */
class RoutingNormalizer : EventNormalizer {

    private val mappers: MutableMap<String, EventNormalizer> = HashMap()

    init {
        register("ecs", EcsNormalizer())
        register("ocsf", OcsfNormalizer())
        register(CloudTrailToEcsMapper.SCHEMA, CloudTrailToEcsMapper())
        register(K8sAuditToEcsMapper.SCHEMA, K8sAuditToEcsMapper())
    }

    fun register(schema: String, mapper: EventNormalizer) {
        mappers[schema.lowercase(Locale.ROOT)] = mapper
    }

    override fun normalize(raw: RawEvent): LogEvent {
        val key = raw.schema.lowercase(Locale.ROOT)
        val mapper = mappers[key]
            ?: throw EventNormalizer.UnsupportedSchemaException(raw.schema)
        return mapper.normalize(raw)
    }
}
