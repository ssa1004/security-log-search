package com.example.security.e2e

import com.example.security.SecurityLogSearchApplication
import com.example.security.application.port.`in`.IngestLogEventUseCase
import com.example.security.application.port.`in`.OperatorContext
import com.example.security.application.port.`in`.OperatorContext.Role
import com.example.security.application.port.`in`.SearchLogEventsUseCase
import com.example.security.application.query.SearchQuery
import com.example.security.domain.common.TenantId
import com.example.security.domain.event.RawEvent
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName

/**
 * 통합 시나리오 — Postgres + Kafka 기반.
 *
 * OpenSearch / ClickHouse 는 별도 통합 테스트로 분리 (의존성 무거움). 본 테스트는 ingest →
 * Kafka publish 까지 검증.
 */
@SpringBootTest(classes = [SecurityLogSearchApplication::class])
@ActiveProfiles("integration")
@Testcontainers
@Tag("integration")
class IngestSearchIntegrationTest {

    @Autowired lateinit var ingestUseCase: IngestLogEventUseCase

    @Autowired lateinit var searchUseCase: SearchLogEventsUseCase

    @Test
    fun ingest_정상_시나리오() {
        // 시드된 default tenant 'acme' 사용 (V2__seed_default_tenant.sql).
        val payload: MutableMap<String, Any> = HashMap()
        payload["event.category"] = "authentication"
        payload["event.action"] = "logon"
        payload["event.outcome"] = "failure"
        payload["event.severity"] = 70
        payload["source.ip"] = "192.168.1.10"
        payload["user.name"] = "alice"
        payload["message"] = "Failed login"

        val raw =
            RawEvent(
                TenantId.of("acme"),
                Instant.parse("2026-05-09T12:00:00Z"),
                "syslog",
                "ecs",
                payload,
            )

        val result = ingestUseCase.ingest(raw, "test-key-1")

        assertThat(result.eventId).isNotNull()
        assertThat(result.duplicate).isFalse()
    }

    @Test
    fun 같은_idempotency_key_재요청은_중복_차단() {
        val payload: MutableMap<String, Any> = HashMap()
        payload["event.category"] = "process"
        val raw =
            RawEvent(
                TenantId.of("acme"),
                Instant.parse("2026-05-09T12:00:00Z"),
                "edr",
                "ecs",
                payload,
            )

        val first = ingestUseCase.ingest(raw, "duplicate-key")
        val second = ingestUseCase.ingest(raw, "duplicate-key")

        assertThat(second.duplicate).isTrue()
        assertThat(second.eventId).isEqualTo(first.eventId)
    }

    @Test
    fun 검색_use_case는_OpenSearch_disabled_에서도_빈_결과_반환() {
        val query =
            SearchQuery(
                TenantId.of("acme"),
                "*",
                emptyMap(),
                null,
                null,
                emptyList(),
                0,
                50,
                null,
            )
        val operator =
            OperatorContext("alice", TenantId.of("acme"), "127.0.0.1", setOf(Role.OPERATOR))

        // NoOp EventSearchPort 가 동작 — 빈 결과.
        val result = searchUseCase.search(query, operator)
        assertThat(result.hits).isEmpty()
    }

    // @Configuration 클래스는 Spring 이 CGLIB 로 subclass 해 @Bean 메서드를 가로채므로 open 이어야
    // 한다. e2e-tests 모듈은 kotlin("plugin.spring") 를 적용하지 않아 자동 open 처리가 안 된다.
    @Configuration
    open class TestConfig {
        // 추후 OpenSearch / ClickHouse 컨테이너 추가 시 여기서 ServiceConnection 정의.
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))

        @Container
        @ServiceConnection
        @JvmField
        val kafka: ConfluentKafkaContainer =
            ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
    }
}
