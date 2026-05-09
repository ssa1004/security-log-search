package com.example.security.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TenantIdTest {

  @Test
  void 정상_생성() {
    var id = TenantId.of("acme-corp");
    assertThat(id.value()).isEqualTo("acme-corp");
    assertThat(id.toString()).isEqualTo("acme-corp");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "a",
        "-bad",
        "bad-",
        "BadCase",
        "ten ant",
        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", // 35자
        ""
      })
  void 잘못된_형식은_거부(String invalid) {
    assertThatThrownBy(() -> TenantId.of(invalid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
  }

  @Test
  void null_거부() {
    assertThatThrownBy(() -> TenantId.of(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void 동등성_value_기반() {
    assertThat(TenantId.of("acme")).isEqualTo(TenantId.of("acme"));
    assertThat(TenantId.of("acme")).isNotEqualTo(TenantId.of("other"));
  }
}
