package com.example.security.domain.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.RawEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OcsfNormalizerTest {

  private final OcsfNormalizer normalizer = new OcsfNormalizer();

  @Test
  void OCSF_authentication_event_정규화() {
    java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("class_uid", 3002); // Authentication
    payload.put("activity_id", 1); // Logon
    payload.put("status_id", 2); // Failure
    payload.put("severity_id", 4); // High
    payload.put("time", 1715252400000L);
    java.util.Map<String, Object> src = new java.util.HashMap<>();
    src.put("ip", "10.0.0.5");
    src.put("port", 22);
    payload.put("src_endpoint", src);
    payload.put("actor", java.util.Map.of("user", java.util.Map.of("name", "bob")));
    java.util.Map<String, Object> device = new java.util.HashMap<>();
    device.put("hostname", "edr-host-7");
    device.put("os", java.util.Map.of("name", "windows"));
    payload.put("device", device);
    var raw =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "edr",
            "ocsf",
            payload);

    var event = normalizer.normalize(raw);
    assertThat(event.eventCategory()).isEqualTo("authentication");
    assertThat(event.eventAction()).isEqualTo("logon");
    assertThat(event.eventOutcome()).isEqualTo("failure");
    assertThat(event.severity()).isEqualTo(Severity.HIGH);
    assertThat(event.sourceIp()).isEqualTo("10.0.0.5");
    assertThat(event.userName()).isEqualTo("bob");
    assertThat(event.hostName()).isEqualTo("edr-host-7");
    assertThat(event.hostOs()).isEqualTo("windows");
    assertThat(event.labels()).containsEntry("ocsf.class_uid", "3002");
  }

  @Test
  void unknown_class_uid_은_unknown_category() {
    java.util.Map<String, Object> payload = new java.util.HashMap<>();
    payload.put("class_uid", 99999);
    payload.put("activity_id", 1);
    var raw =
        new RawEvent(
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "vendor-z",
            "ocsf",
            payload);
    var event = normalizer.normalize(raw);
    assertThat(event.eventCategory()).isEqualTo("unknown");
  }

  @Test
  void severity_id_매핑() {
    var cases = new java.util.LinkedHashMap<Integer, Severity>();
    cases.put(1, Severity.INFO);
    cases.put(2, Severity.LOW);
    cases.put(3, Severity.MEDIUM);
    cases.put(4, Severity.HIGH);
    cases.put(5, Severity.CRITICAL);
    for (var entry : cases.entrySet()) {
      java.util.Map<String, Object> payload = new java.util.HashMap<>();
      payload.put("class_uid", 3002);
      payload.put("severity_id", entry.getKey());
      var raw =
          new RawEvent(
              TenantId.of("acme"),
              Instant.parse("2026-05-09T12:00:00Z"),
              "x",
              "ocsf",
              payload);
      var event = normalizer.normalize(raw);
      assertThat(event.severity())
          .as("OCSF severity_id %d", entry.getKey())
          .isEqualTo(entry.getValue());
    }
  }
}
