package com.example.security.application.sigma;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.sigma.SigmaToAlertRuleMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * scripts/sample-sigma-rules/ 의 실제 데모 YAML 파일이 parser + mapper 를 정상 통과하는지
 * 검증한다. 데모 파일이 stale 해지면 본 테스트가 실패하므로, 매퍼 변경 시 데모를 반드시
 * 같이 갱신하도록 강제하는 안전망 역할.
 */
class SampleSigmaRulesIntegrationTest {

  private static final Path SAMPLES_DIR =
      Path.of("..", "scripts", "sample-sigma-rules").toAbsolutePath().normalize();

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-09T12:00:00Z"), ZoneOffset.UTC);
  private final SigmaYamlParser parser = new SigmaYamlParser(clock);
  private final SigmaToAlertRuleMapper mapper = new SigmaToAlertRuleMapper();
  private final TenantId tenant = TenantId.of("acme");

  @Test
  void brute_force_샘플은_fully_supported_AlertRule_로_변환된다() throws IOException {
    var yaml = Files.readString(SAMPLES_DIR.resolve("auth_failed_brute_force.yml"));
    var sigma = parser.parseSingle(yaml);

    var result = mapper.map(sigma, tenant, clock.instant());

    assertThat(result.fullySupported()).as("unsupported = %s", result.unsupported()).isTrue();
    assertThat(result.rule().enabled()).isTrue();
    assertThat(result.rule().severity()).isEqualTo(Severity.HIGH);
    assertThat(result.rule().filterCategory()).isEqualTo("authentication");
    assertThat(result.rule().filterAction()).isEqualTo("logon");
    assertThat(result.rule().filterOutcome()).isEqualTo("failure");
    assertThat(result.rule().groupByField()).isEqualTo("source.ip");
    // mapper 의 default 값 — 변경되면 본 테스트가 안전망 역할.
    assertThat(result.rule().threshold()).isEqualTo(5);
    assertThat(result.rule().window()).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void port_scan_샘플은_network_카테고리로_변환된다() throws IOException {
    var yaml = Files.readString(SAMPLES_DIR.resolve("network_port_scan.yml"));
    var sigma = parser.parseSingle(yaml);

    var result = mapper.map(sigma, tenant, clock.instant());

    assertThat(result.fullySupported()).as("unsupported = %s", result.unsupported()).isTrue();
    assertThat(result.rule().filterCategory()).isEqualTo("network");
    assertThat(result.rule().severity()).isEqualTo(Severity.MEDIUM);
  }

  @Test
  void powershell_샘플은_process_카테고리_critical_severity_로_변환된다() throws IOException {
    var yaml = Files.readString(SAMPLES_DIR.resolve("process_suspicious_powershell.yml"));
    var sigma = parser.parseSingle(yaml);

    var result = mapper.map(sigma, tenant, clock.instant());

    assertThat(result.fullySupported()).as("unsupported = %s", result.unsupported()).isTrue();
    assertThat(result.rule().filterCategory()).isEqualTo("process");
    assertThat(result.rule().severity()).isEqualTo(Severity.CRITICAL);
    assertThat(result.rule().filterAction()).isEqualTo("process_started");
  }

  @Test
  void admin_unusual_logon_샘플은_timeframe_미지원으로_disabled_된다() throws IOException {
    var yaml = Files.readString(SAMPLES_DIR.resolve("auth_unusual_admin_logon.yml"));
    var sigma = parser.parseSingle(yaml);

    var result = mapper.map(sigma, tenant, clock.instant());

    // timeframe 키가 detection 안에 있어 mapper 가 unsupported 로 기록 → enabled=false.
    assertThat(result.unsupported())
        .anySatisfy(u -> assertThat(u).contains("timeframe"));
    assertThat(result.rule().enabled()).isFalse();
    // user.name selection 이 groupByField 를 source.ip → user.name 으로 옮긴다.
    assertThat(result.rule().groupByField()).isEqualTo("user.name");
  }

  @Test
  void 모든_샘플이_multi_document_로_묶어도_parse_된다() throws IOException {
    var combined = new StringBuilder();
    var files =
        Files.list(SAMPLES_DIR)
            .filter(p -> p.toString().endsWith(".yml"))
            .sorted()
            .toList();
    for (int i = 0; i < files.size(); i++) {
      if (i > 0) combined.append("\n---\n");
      combined.append(Files.readString(files.get(i)));
    }
    var rules = parser.parseAll(combined.toString());
    assertThat(rules).hasSize(files.size());
  }

}
