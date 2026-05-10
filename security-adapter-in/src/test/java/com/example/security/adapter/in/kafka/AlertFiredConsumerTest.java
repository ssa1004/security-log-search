package com.example.security.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.security.adapter.in.metrics.SecurityLogMetrics;
import com.example.security.application.port.in.EvaluateAlertUseCase;
import com.example.security.application.port.out.IdempotencyPort;
import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.Alert.AlertStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AlertFiredConsumerTest {

  private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void Flink_가_보낸_payload_파싱() {
    var consumer =
        new AlertFiredConsumer(
            Mockito.mock(EvaluateAlertUseCase.class),
            alwaysClaimingIdempotency(),
            json,
            new SecurityLogMetrics(new SimpleMeterRegistry()));

    var alert = consumer.parseAlert(samplePayload());

    assertThat(alert.tenantId().value()).isEqualTo("acme");
    assertThat(alert.severity()).isEqualTo(Severity.HIGH);
    assertThat(alert.matchedCount()).isEqualTo(7);
    assertThat(alert.status()).isEqualTo(AlertStatus.OPEN);
    assertThat(alert.triggeringEventIds()).hasSize(2);
  }

  @Test
  void at_least_once_재전송_시_use_case_는_한_번만_호출된다() {
    // Flink Kafka sink 가 AT_LEAST_ONCE 인 만큼, 같은 alertId 가 두 번 도착할 수 있다.
    // IdempotencyPort 가 두 번째 claim 을 차단해 use case / metric 모두 한 번만 실행되어야 한다.
    var useCase = Mockito.mock(EvaluateAlertUseCase.class);
    var idempotency = Mockito.mock(IdempotencyPort.class);
    var registry = new SimpleMeterRegistry();
    var metrics = new SecurityLogMetrics(registry);
    var consumer = new AlertFiredConsumer(useCase, idempotency, json, metrics);

    when(idempotency.tryClaim(any(TenantId.class), any(String.class), any()))
        .thenReturn(true) // 첫 메시지 — claim 성공
        .thenReturn(false); // 두 번째 — 이미 claim 됨

    consumer.onMessage(samplePayload(), 0, 0L);
    consumer.onMessage(samplePayload(), 0, 1L);

    verify(useCase, times(1)).handleFired(any());
    assertThat(registry.find(SecurityLogMetrics.ALERT_FIRED_TOTAL).counter().count()).isEqualTo(1.0);
    assertThat(registry.find(SecurityLogMetrics.ALERT_DUPLICATE_TOTAL).counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void 멱등성_키는_alertId_와_tenant_로_scoped_된다() {
    // IdempotencyPort.tryClaim 이 (tenantId, key) 로 호출되는지 확인 — 다른 tenant 의 같은
    // alertId (사실상 거의 발생 안 함이지만) 가 충돌하지 않도록.
    var useCase = Mockito.mock(EvaluateAlertUseCase.class);
    var idempotency = Mockito.mock(IdempotencyPort.class);
    when(idempotency.tryClaim(any(TenantId.class), any(String.class), any())).thenReturn(true);
    var consumer =
        new AlertFiredConsumer(
            useCase, idempotency, json, new SecurityLogMetrics(new SimpleMeterRegistry()));

    consumer.onMessage(samplePayload(), 0, 0L);

    verify(idempotency)
        .tryClaim(
            eq(TenantId.of("acme")),
            eq(AlertFiredConsumer.IDEMPOTENCY_KEY_PREFIX + "11111111-1111-1111-1111-111111111111"),
            any());
  }

  @Test
  void 중복_차단_시_use_case_audit_notification_경로_미진입() {
    var useCase = Mockito.mock(EvaluateAlertUseCase.class);
    var idempotency = Mockito.mock(IdempotencyPort.class);
    when(idempotency.tryClaim(any(TenantId.class), any(String.class), any())).thenReturn(false);
    var consumer =
        new AlertFiredConsumer(
            useCase, idempotency, json, new SecurityLogMetrics(new SimpleMeterRegistry()));

    consumer.onMessage(samplePayload(), 0, 0L);

    verify(useCase, never()).handleFired(any());
  }

  private static IdempotencyPort alwaysClaimingIdempotency() {
    var port = Mockito.mock(IdempotencyPort.class);
    when(port.tryClaim(any(TenantId.class), any(String.class), any())).thenReturn(true);
    return port;
  }

  private static String samplePayload() {
    return """
        {
          "alertId": "11111111-1111-1111-1111-111111111111",
          "tenantId": "acme",
          "ruleId": "22222222-2222-2222-2222-222222222222",
          "ruleName": "5분 안 5회 인증 실패",
          "severity": "HIGH",
          "groupKey": "192.168.1.10",
          "groupByField": "source.ip",
          "matchedCount": 7,
          "windowStart": "2026-05-09T11:55:00Z",
          "windowEnd": "2026-05-09T12:00:00Z",
          "firedAt": "2026-05-09T12:00:01Z",
          "status": "OPEN",
          "triggeringEventIds": [
            "33333333-3333-3333-3333-333333333333",
            "44444444-4444-4444-4444-444444444444"
          ],
          "message": "brute-force 의심"
        }
        """;
  }
}
