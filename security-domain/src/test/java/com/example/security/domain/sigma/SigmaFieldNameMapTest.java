package com.example.security.domain.sigma;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SigmaFieldNameMapTest {

  @Test
  void Windows_eventid_는_event_code_로() {
    assertThat(SigmaFieldNameMap.toEcs("EventID")).isEqualTo("event.code");
    assertThat(SigmaFieldNameMap.toEcs("eventid")).isEqualTo("event.code");
  }

  @Test
  void Windows_TargetUserName_은_user_name_으로() {
    assertThat(SigmaFieldNameMap.toEcs("TargetUserName")).isEqualTo("user.name");
  }

  @Test
  void IpAddress_는_source_ip_로() {
    assertThat(SigmaFieldNameMap.toEcs("IpAddress")).isEqualTo("source.ip");
  }

  @Test
  void 매핑_없는_필드는_원본_유지() {
    assertThat(SigmaFieldNameMap.toEcs("CustomVendorField")).isEqualTo("CustomVendorField");
  }

  @Test
  void 이미_ECS_형식인_필드는_그대로() {
    assertThat(SigmaFieldNameMap.toEcs("source.ip")).isEqualTo("source.ip");
    assertThat(SigmaFieldNameMap.toEcs("event.action")).isEqualTo("event.action");
  }
}
