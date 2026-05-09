package com.example.security.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.adapter.in.metrics.SecurityLogMetrics;
import com.example.security.application.port.in.EvaluateAlertUseCase;
import com.example.security.domain.common.Severity;
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
            json,
            new SecurityLogMetrics(new SimpleMeterRegistry()));

    var payload =
        """
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

    var alert = consumer.parseAlert(payload);

    assertThat(alert.tenantId().value()).isEqualTo("acme");
    assertThat(alert.severity()).isEqualTo(Severity.HIGH);
    assertThat(alert.matchedCount()).isEqualTo(7);
    assertThat(alert.status()).isEqualTo(AlertStatus.OPEN);
    assertThat(alert.triggeringEventIds()).hasSize(2);
  }
}
