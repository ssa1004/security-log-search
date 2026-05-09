package com.example.security.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LogEventTest {

  @Test
  void index_alias_naming() {
    var event = base().build();
    assertThat(event.openSearchIndexName()).isEqualTo("events-acme-2026.05.09");
    assertThat(event.openSearchWriteAlias()).isEqualTo("events-acme-write");
  }

  @Test
  void 인증_실패_여부() {
    var failure =
        base().eventCategory("authentication").eventOutcome("failure").build();
    var success =
        base().eventCategory("authentication").eventOutcome("success").build();
    var network = base().eventCategory("network").eventOutcome("denied").build();

    assertThat(failure.isAuthFailure()).isTrue();
    assertThat(success.isAuthSuccess()).isTrue();
    assertThat(network.isAuthFailure()).isFalse();
  }

  @Test
  void timestamp_가_ingestedAt_보다_60초_이상_미래면_거부() {
    var ingestedAt = Instant.parse("2026-05-09T12:00:00Z");
    var futureTs = ingestedAt.plusSeconds(120);
    assertThatThrownBy(() -> base().timestamp(futureTs).ingestedAt(ingestedAt).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("미래");
  }

  @Test
  void labels_방어적_복사() {
    var mutable = new java.util.HashMap<String, String>();
    mutable.put("k1", "v1");
    var event = base().labels(mutable).build();
    mutable.put("k2", "v2");
    assertThat(event.labels()).hasSize(1).containsEntry("k1", "v1");
  }

  static EventBuilder base() {
    return new EventBuilder();
  }

  static class EventBuilder {
    UUID eventId = UUID.randomUUID();
    TenantId tenantId = TenantId.of("acme");
    Instant timestamp = Instant.parse("2026-05-09T12:00:00Z");
    Instant ingestedAt = Instant.parse("2026-05-09T12:00:01Z");
    String eventKind = "event";
    String eventCategory = "authentication";
    String eventType = "info";
    String eventAction = "logon";
    String eventOutcome = "success";
    Severity severity = Severity.MEDIUM;
    String sourceIp = "10.0.0.1";
    Integer sourcePort = 12345;
    String destinationIp = "10.0.0.2";
    Integer destinationPort = 22;
    String userName = "alice";
    String hostName = "host-1";
    String hostOs = "linux";
    String message = "msg";
    Map<String, String> labels = Map.of();

    EventBuilder eventCategory(String v) {
      this.eventCategory = v;
      return this;
    }

    EventBuilder eventOutcome(String v) {
      this.eventOutcome = v;
      return this;
    }

    EventBuilder timestamp(Instant v) {
      this.timestamp = v;
      return this;
    }

    EventBuilder ingestedAt(Instant v) {
      this.ingestedAt = v;
      return this;
    }

    EventBuilder labels(Map<String, String> v) {
      this.labels = v;
      return this;
    }

    LogEvent build() {
      return new LogEvent(
          eventId,
          tenantId,
          timestamp,
          ingestedAt,
          eventKind,
          eventCategory,
          eventType,
          eventAction,
          eventOutcome,
          severity,
          sourceIp,
          sourcePort,
          destinationIp,
          destinationPort,
          userName,
          hostName,
          hostOs,
          message,
          labels);
    }
  }
}
