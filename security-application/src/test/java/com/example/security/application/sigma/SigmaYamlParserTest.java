package com.example.security.application.sigma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SigmaYamlParserTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-09T12:00:00Z"), ZoneOffset.UTC);
  private final SigmaYamlParser parser = new SigmaYamlParser(clock);

  @Test
  void Windows_4625_샘플_parse() {
    var yaml =
        """
        title: Failed Logon From Public IP
        id: 9d9d9d9d-1234-5678-9abc-def012345678
        status: stable
        description: 외부 IP 에서 4625 가 자주 발생
        author: SigmaHQ
        references:
          - https://attack.mitre.org/techniques/T1110/
        tags:
          - attack.credential_access
          - attack.t1110
        logsource:
          category: authentication
          product: windows
        detection:
          selection:
            EventID: 4625
            TargetUserName: 'Administrator'
          condition: selection
        falsepositives:
          - 운영자 패스워드 입력 실수
        level: high
        """;

    var rule = parser.parseSingle(yaml);

    assertThat(rule.id()).isEqualTo("9d9d9d9d-1234-5678-9abc-def012345678");
    assertThat(rule.title()).isEqualTo("Failed Logon From Public IP");
    assertThat(rule.level()).isEqualTo("high");
    assertThat(rule.status()).isEqualTo("stable");
    assertThat(rule.author()).isEqualTo("SigmaHQ");
    assertThat(rule.tags()).contains("attack.t1110");
    assertThat(rule.references()).hasSize(1);
    assertThat(rule.logsource()).containsEntry("category", "authentication");
    assertThat(rule.detection()).containsKey("selection");
    assertThat(rule.condition()).isEqualTo("selection");
    assertThat(rule.importedAt()).isEqualTo(Instant.parse("2026-05-09T12:00:00Z"));
  }

  @Test
  void multi_document_YAML_도_지원() {
    var yaml =
        """
        title: Rule 1
        id: 11111111-1111-1111-1111-111111111111
        logsource:
          category: authentication
        detection:
          selection:
            EventID: 4625
          condition: selection
        level: high
        ---
        title: Rule 2
        id: 22222222-2222-2222-2222-222222222222
        logsource:
          category: process_creation
        detection:
          selection:
            Image: 'powershell.exe'
          condition: selection
        level: medium
        """;

    var rules = parser.parseAll(yaml);

    assertThat(rules).hasSize(2);
    assertThat(rules.get(0).title()).isEqualTo("Rule 1");
    assertThat(rules.get(1).title()).isEqualTo("Rule 2");
  }

  @Test
  void title_없으면_거부() {
    var yaml =
        """
        id: 11111111-1111-1111-1111-111111111111
        detection:
          selection:
            EventID: 4625
          condition: selection
        """;
    assertThatThrownBy(() -> parser.parseSingle(yaml))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("title");
  }

  @Test
  void detection_없으면_거부() {
    var yaml =
        """
        title: x
        id: 11111111-1111-1111-1111-111111111111
        """;
    assertThatThrownBy(() -> parser.parseSingle(yaml))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("detection");
  }

  @Test
  void id_없으면_랜덤_생성() {
    var yaml =
        """
        title: ID-less rule
        detection:
          selection:
            EventID: 4625
          condition: selection
        """;
    var rule = parser.parseSingle(yaml);
    assertThat(rule.id()).isNotBlank();
  }
}
