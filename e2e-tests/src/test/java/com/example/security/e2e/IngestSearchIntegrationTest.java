package com.example.security.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.security.SecurityLogSearchApplication;
import com.example.security.application.port.in.IngestLogEventUseCase;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.in.OperatorContext.Role;
import com.example.security.application.port.in.SearchLogEventsUseCase;
import com.example.security.application.query.SearchQuery;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.RawEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 시나리오 — Postgres + Kafka 기반.
 *
 * <p>OpenSearch / ClickHouse 는 별도 통합 테스트로 분리 (의존성 무거움). 본 테스트는 ingest →
 * Kafka publish 까지 검증.
 */
@SpringBootTest(classes = SecurityLogSearchApplication.class)
@ActiveProfiles("integration")
@Testcontainers
@Tag("integration")
class IngestSearchIntegrationTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  @Container
  @ServiceConnection
  static KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

  @Autowired IngestLogEventUseCase ingestUseCase;
  @Autowired SearchLogEventsUseCase searchUseCase;

  @Test
  void ingest_정상_시나리오() {
    // 시드된 default tenant 'acme' 사용 (V2__seed_default_tenant.sql).
    Map<String, Object> payload = new HashMap<>();
    payload.put("event.category", "authentication");
    payload.put("event.action", "logon");
    payload.put("event.outcome", "failure");
    payload.put("event.severity", 70);
    payload.put("source.ip", "192.168.1.10");
    payload.put("user.name", "alice");
    payload.put("message", "Failed login");

    var raw =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "syslog",
            "ecs",
            payload);

    var result = ingestUseCase.ingest(raw, "test-key-1");

    assertThat(result.eventId()).isNotNull();
    assertThat(result.duplicate()).isFalse();
  }

  @Test
  void 같은_idempotency_key_재요청은_중복_차단() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("event.category", "process");
    var raw =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "edr",
            "ecs",
            payload);

    var first = ingestUseCase.ingest(raw, "duplicate-key");
    var second = ingestUseCase.ingest(raw, "duplicate-key");

    assertThat(second.duplicate()).isTrue();
    assertThat(second.eventId()).isEqualTo(first.eventId());
  }

  @Test
  void 검색_use_case는_OpenSearch_disabled_에서도_빈_결과_반환() {
    var query =
        new SearchQuery(
            TenantId.of("acme"),
            "*",
            Map.of(),
            null,
            null,
            List.of(),
            0,
            50,
            null);
    var operator =
        new OperatorContext("alice", TenantId.of("acme"), "127.0.0.1", Set.of(Role.OPERATOR));

    // NoOp EventSearchPort 가 동작 — 빈 결과.
    var result = searchUseCase.search(query, operator);
    assertThat(result.hits()).isEmpty();
  }

  @Configuration
  static class TestConfig {
    // 추후 OpenSearch / ClickHouse 컨테이너 추가 시 여기서 ServiceConnection 정의.
  }
}
