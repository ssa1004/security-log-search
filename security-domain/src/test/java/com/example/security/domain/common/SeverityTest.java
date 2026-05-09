package com.example.security.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SeverityTest {

  @ParameterizedTest
  @CsvSource({
    "0, INFO",
    "10, INFO",
    "19, INFO",
    "20, LOW",
    "30, LOW",
    "39, LOW",
    "40, MEDIUM",
    "59, MEDIUM",
    "60, HIGH",
    "79, HIGH",
    "80, CRITICAL",
    "100, CRITICAL"
  })
  void ECS_score_경계값_매핑(int score, Severity expected) {
    assertThat(Severity.fromEcsScore(score)).isEqualTo(expected);
  }

  @Test
  void 범위_초과_거부() {
    assertThatThrownBy(() -> Severity.fromEcsScore(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Severity.fromEcsScore(101))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
