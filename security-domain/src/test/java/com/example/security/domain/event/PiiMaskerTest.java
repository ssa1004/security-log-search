package com.example.security.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.tenant.Tenant.PiiMaskingPolicy;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PiiMaskerTest {

  @Test
  void IPv4_마지막_옥텟_마스킹() {
    assertThat(PiiMasker.maskIp("192.168.1.10")).isEqualTo("192.168.1.***");
  }

  @Test
  void IPv6_마지막_그룹_마스킹() {
    assertThat(PiiMasker.maskIp("fe80::1234")).isEqualTo("fe80::****");
  }

  @Test
  void username_가운데_마스킹() {
    assertThat(PiiMasker.maskUser("alice")).isEqualTo("a***e");
    assertThat(PiiMasker.maskUser("ab")).isEqualTo("ab"); // 너무 짧으면 그대로
  }

  @Test
  void 이메일_마스킹() {
    var msg = "Login failed for user@example.com from 1.2.3.4";
    assertThat(PiiMasker.maskEmailInMessage(msg)).contains("***@***").doesNotContain("user@example.com");
  }

  @Test
  void NONE_정책은_변경_없음() {
    var event = sampleEvent();
    assertThat(PiiMasker.mask(event, PiiMaskingPolicy.NONE)).isEqualTo(event);
  }

  @Test
  void IP_ONLY_정책은_IP만_마스킹() {
    var event = sampleEvent();
    var masked = PiiMasker.mask(event, PiiMaskingPolicy.IP_ONLY);
    assertThat(masked.sourceIp()).isEqualTo("192.168.1.***");
    assertThat(masked.userName()).isEqualTo("alice"); // 변경 없음
  }

  @Test
  void STRICT_정책은_사용자명도_마스킹() {
    var event = sampleEvent();
    var masked = PiiMasker.mask(event, PiiMaskingPolicy.STRICT);
    assertThat(masked.sourceIp()).isEqualTo("192.168.1.***");
    assertThat(masked.userName()).isEqualTo("a***e");
  }

  private LogEvent sampleEvent() {
    return new LogEvent(
        UUID.randomUUID(),
        TenantId.of("acme"),
        Instant.parse("2026-05-09T12:00:00Z"),
        Instant.parse("2026-05-09T12:00:01Z"),
        "event",
        "authentication",
        "info",
        "logon",
        "failure",
        Severity.MEDIUM,
        "192.168.1.10",
        12345,
        null,
        null,
        "alice",
        "host-1",
        "linux",
        "Login failed",
        Map.of());
  }
}
