package com.example.security.domain.mapping;

import com.example.security.domain.event.LogEvent;
import com.example.security.domain.event.RawEvent;
import com.example.security.domain.mapping.source.CloudTrailToEcsMapper;
import com.example.security.domain.mapping.source.K8sAuditToEcsMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * schema 힌트로 매퍼를 라우팅하는 컴포지트. application layer 에서 단일 entry point 로 사용.
 *
 * <p>등록 매퍼:
 *
 * <ul>
 *   <li>{@code ecs} — {@link EcsNormalizer} (ECS 가 이미 정규화된 dotted notation 입력)
 *   <li>{@code ocsf} — {@link OcsfNormalizer} (OCSF 표준 → ECS)
 *   <li>{@code aws-cloudtrail} — {@link CloudTrailToEcsMapper}
 *   <li>{@code k8s-audit} — {@link K8sAuditToEcsMapper}
 * </ul>
 */
public class RoutingNormalizer implements EventNormalizer {

  private final Map<String, EventNormalizer> mappers;

  public RoutingNormalizer() {
    this.mappers = new HashMap<>();
    register("ecs", new EcsNormalizer());
    register("ocsf", new OcsfNormalizer());
    register(CloudTrailToEcsMapper.SCHEMA, new CloudTrailToEcsMapper());
    register(K8sAuditToEcsMapper.SCHEMA, new K8sAuditToEcsMapper());
  }

  public final void register(String schema, EventNormalizer mapper) {
    mappers.put(Objects.requireNonNull(schema).toLowerCase(java.util.Locale.ROOT), mapper);
  }

  @Override
  public LogEvent normalize(RawEvent raw) {
    var key = raw.schema().toLowerCase(java.util.Locale.ROOT);
    var mapper = mappers.get(key);
    if (mapper == null) {
      throw new UnsupportedSchemaException(raw.schema());
    }
    return mapper.normalize(raw);
  }
}
