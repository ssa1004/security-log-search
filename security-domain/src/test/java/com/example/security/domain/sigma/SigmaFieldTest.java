package com.example.security.domain.sigma;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SigmaFieldTest {

  @Test
  void modifier_없으면_equals_like() {
    var f = SigmaField.parse("EventID");
    assertThat(f.name()).isEqualTo("EventID");
    assertThat(f.isEqualsLike()).isTrue();
  }

  @Test
  void contains_modifier_는_equals_like() {
    var f = SigmaField.parse("CommandLine|contains");
    assertThat(f.modifiers()).containsExactly("contains");
    assertThat(f.isEqualsLike()).isTrue();
  }

  @Test
  void startswith_endswith_도_equals_like() {
    assertThat(SigmaField.parse("Image|endswith").isEqualsLike()).isTrue();
    assertThat(SigmaField.parse("Image|startswith").isEqualsLike()).isTrue();
  }

  @Test
  void re_modifier_는_미지원() {
    var f = SigmaField.parse("CommandLine|re");
    assertThat(f.isEqualsLike()).isFalse();
  }

  @Test
  void base64_modifier_는_미지원() {
    var f = SigmaField.parse("CommandLine|base64");
    assertThat(f.isEqualsLike()).isFalse();
  }
}
