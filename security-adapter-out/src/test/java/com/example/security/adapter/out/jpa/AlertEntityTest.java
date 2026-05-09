package com.example.security.adapter.out.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.adapter.out.jpa.entity.AlertEntity;
import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.Alert;
import com.example.security.domain.rule.Alert.AlertStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertEntityTest {

  @Test
  void round_trip_with_triggering_event_ids() {
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    var alert =
        new Alert(
            UUID.randomUUID(),
            TenantId.of("acme"),
            UUID.randomUUID(),
            "rule-x",
            Severity.HIGH,
            "192.168.1.10",
            "source.ip",
            5,
            Instant.parse("2026-05-09T11:55:00Z"),
            Instant.parse("2026-05-09T12:00:00Z"),
            Instant.parse("2026-05-09T12:00:01Z"),
            AlertStatus.OPEN,
            ids,
            "msg");
    var rt = AlertEntity.from(alert).toDomain();
    assertThat(rt.triggeringEventIds()).containsExactlyElementsOf(ids);
    assertThat(rt.status()).isEqualTo(AlertStatus.OPEN);
  }

  @Test
  void round_trip_with_empty_ids() {
    var alert =
        new Alert(
            UUID.randomUUID(),
            TenantId.of("acme"),
            UUID.randomUUID(),
            "rule-x",
            Severity.HIGH,
            "192.168.1.10",
            "source.ip",
            5,
            Instant.parse("2026-05-09T11:55:00Z"),
            Instant.parse("2026-05-09T12:00:00Z"),
            Instant.parse("2026-05-09T12:00:01Z"),
            AlertStatus.OPEN,
            List.of(),
            "msg");
    assertThat(AlertEntity.from(alert).toDomain().triggeringEventIds()).isEmpty();
  }
}
