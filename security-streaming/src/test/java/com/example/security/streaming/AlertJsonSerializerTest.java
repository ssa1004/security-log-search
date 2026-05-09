package com.example.security.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.Alert;
import com.example.security.domain.rule.Alert.AlertStatus;
import com.example.security.streaming.serde.AlertJsonSerializer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertJsonSerializerTest {

  @Test
  void Spring_consumer_가_읽을_수_있는_형태로_직렬화() {
    var alert = sample();
    var serializer = new AlertJsonSerializer();
    var json = serializer.serializeToString(alert);

    assertThat(json).contains("\"alertId\":");
    assertThat(json).contains("\"tenantId\":\"acme\"");
    assertThat(json).contains("\"severity\":\"HIGH\"");
    assertThat(json).contains("\"matchedCount\":5");
    assertThat(json).contains("\"status\":\"OPEN\"");
  }

  private Alert sample() {
    return new Alert(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        TenantId.of("acme"),
        UUID.fromString("22222222-2222-2222-2222-222222222222"),
        "5분 안 5회 인증 실패",
        Severity.HIGH,
        "192.168.1.10",
        "source.ip",
        5,
        Instant.parse("2026-05-09T11:55:00Z"),
        Instant.parse("2026-05-09T12:00:00Z"),
        Instant.parse("2026-05-09T12:00:01Z"),
        AlertStatus.OPEN,
        List.of(UUID.fromString("33333333-3333-3333-3333-333333333333")),
        "brute-force 의심");
  }
}
