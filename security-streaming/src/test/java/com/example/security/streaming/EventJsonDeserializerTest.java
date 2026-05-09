package com.example.security.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.domain.common.Severity;
import com.example.security.streaming.serde.EventJsonDeserializer;
import org.junit.jupiter.api.Test;

class EventJsonDeserializerTest {

  @Test
  void Spring_publisher_가_보낸_payload_역직렬화() throws Exception {
    var json =
        """
        {
          "eventId": "11111111-1111-1111-1111-111111111111",
          "tenantId": { "value": "acme" },
          "timestamp": "2026-05-09T12:00:00Z",
          "ingestedAt": "2026-05-09T12:00:01Z",
          "eventKind": "event",
          "eventCategory": "authentication",
          "eventType": "denied",
          "eventAction": "logon",
          "eventOutcome": "failure",
          "severity": "HIGH",
          "sourceIp": "192.168.1.10",
          "sourcePort": 12345,
          "destinationIp": null,
          "destinationPort": null,
          "userName": "alice",
          "hostName": "host-1",
          "hostOs": "linux",
          "message": "Failed login",
          "labels": { "raw": "ssh-bf" }
        }
        """;
    var event = new EventJsonDeserializer().deserialize(json);
    assertThat(event.tenantId().value()).isEqualTo("acme");
    assertThat(event.severity()).isEqualTo(Severity.HIGH);
    assertThat(event.sourceIp()).isEqualTo("192.168.1.10");
    assertThat(event.userName()).isEqualTo("alice");
    assertThat(event.labels()).containsEntry("raw", "ssh-bf");
  }
}
