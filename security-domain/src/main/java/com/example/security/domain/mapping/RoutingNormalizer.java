package com.example.security.domain.mapping;

import com.example.security.domain.event.LogEvent;
import com.example.security.domain.event.RawEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * schema 힌트로 매퍼를 라우팅하는 컴포지트. application layer 에서 단일 entry point 로 사용.
 */
public class RoutingNormalizer implements EventNormalizer {

  private final Map<String, EventNormalizer> mappers;

  public RoutingNormalizer() {
    this.mappers = new HashMap<>();
    register("ecs", new EcsNormalizer());
    register("ocsf", new OcsfNormalizer());
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
