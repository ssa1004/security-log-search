package com.example.security.domain.sigma;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.AlertRule.RuleType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SigmaToAlertRuleMapperTest {

  private final SigmaToAlertRuleMapper mapper = new SigmaToAlertRuleMapper();
  private final Instant now = Instant.parse("2026-05-09T12:00:00Z");
  private final TenantId tenantId = TenantId.of("acme");

  @Test
  void Windows_4625_brute_force_룰_변환() {
    var sigma =
        new SigmaRule(
            "f1f8c8ee-3a3e-4b3f-9c2d-2b6c8e1f0a01",
            "Windows Failed Logon",
            "다수 4625 — brute force 의심",
            "stable",
            "high",
            "SigmaHQ",
            List.of("https://attack.mitre.org/techniques/T1110/"),
            List.of("attack.credential_access", "attack.t1110"),
            List.of("운영자 패스워드 입력 실수"),
            Map.of("category", "authentication", "product", "windows"),
            Map.of(
                "selection",
                Map.of("EventID", 4625, "TargetUserName", "Administrator"),
                "condition",
                "selection"),
            List.of("ComputerName", "TargetUserName", "IpAddress"),
            "raw-yaml-here",
            now);

    var result = mapper.map(sigma, tenantId, now);

    assertThat(result.rule().type()).isEqualTo(RuleType.THRESHOLD);
    assertThat(result.rule().filterCategory()).isEqualTo("authentication");
    assertThat(result.rule().severity()).isEqualTo(Severity.HIGH);
    assertThat(result.rule().tenantId()).isEqualTo(tenantId);
    assertThat(result.rule().enabled()).isTrue();
    // EventID, TargetUserName 은 매핑 가능 (event.code, user.name) 이므로 unsupported 비어있어야.
    assertThat(result.unsupported()).isEmpty();
  }

  @Test
  void process_creation_룰_변환() {
    var sigma =
        new SigmaRule(
            "11111111-1111-1111-1111-111111111111",
            "Suspicious cmd.exe",
            "cmd.exe + powershell 인자",
            "test",
            "medium",
            null,
            List.of(),
            List.of("attack.execution"),
            List.of(),
            Map.of("category", "process_creation", "product", "windows"),
            Map.of(
                "selection",
                Map.of("Image", "C:\\\\Windows\\\\System32\\\\cmd.exe"),
                "condition",
                "selection"),
            List.of(),
            "raw",
            now);

    var result = mapper.map(sigma, tenantId, now);

    assertThat(result.rule().filterCategory()).isEqualTo("process");
    assertThat(result.rule().severity()).isEqualTo(Severity.MEDIUM);
    // Image 는 process.executable 로 매핑되는데 우리 룰 DSL 의 group/filter 키가 아님 → unsupported 에 기록.
    assertThat(result.unsupported()).anySatisfy(s -> assertThat(s).contains("필드 미매핑"));
    // unsupported 가 있으면 운영자 검토 필요 → enabled=false.
    assertThat(result.rule().enabled()).isFalse();
  }

  @Test
  void critical_level_매핑() {
    var sigma = baseSigma("c-1", "critical");
    var result = mapper.map(sigma, tenantId, now);
    assertThat(result.rule().severity()).isEqualTo(Severity.CRITICAL);
  }

  @Test
  void low_level_매핑() {
    var sigma = baseSigma("l-1", "low");
    var result = mapper.map(sigma, tenantId, now);
    assertThat(result.rule().severity()).isEqualTo(Severity.LOW);
  }

  @Test
  void level_없으면_MEDIUM() {
    var sigma = baseSigma("n-1", null);
    var result = mapper.map(sigma, tenantId, now);
    assertThat(result.rule().severity()).isEqualTo(Severity.MEDIUM);
  }

  @Test
  void aggregation_condition_은_unsupported_로_기록() {
    var sigma =
        new SigmaRule(
            "agg-1",
            "Failed logons exceeding threshold",
            "Sigma 의 count() aggregation",
            "test",
            "high",
            null,
            List.of(),
            List.of(),
            List.of(),
            Map.of("category", "authentication"),
            Map.of(
                "selection",
                Map.of("EventID", 4625),
                "condition",
                "selection | count() by IpAddress > 5"),
            List.of(),
            "raw",
            now);

    var result = mapper.map(sigma, tenantId, now);
    assertThat(result.unsupported()).isNotEmpty();
    assertThat(result.unsupported())
        .anySatisfy(s -> assertThat(s).containsAnyOf("aggregation", "condition"));
  }

  @Test
  void timeframe_은_default_window_로_대체되고_unsupported_기록() {
    var sigma =
        new SigmaRule(
            "tf-1",
            "Window-bound 룰",
            "10m timeframe",
            "test",
            "medium",
            null,
            List.of(),
            List.of(),
            List.of(),
            Map.of("category", "authentication"),
            Map.of(
                "selection",
                Map.of("EventID", 4625),
                "timeframe",
                "10m",
                "condition",
                "selection"),
            List.of(),
            "raw",
            now);

    var result = mapper.map(sigma, tenantId, now);
    assertThat(result.rule().window()).isEqualTo(Duration.ofMinutes(5)); // default
    assertThat(result.unsupported()).anySatisfy(s -> assertThat(s).contains("timeframe"));
  }

  @Test
  void contains_modifier_는_지원() {
    var sigma =
        new SigmaRule(
            "mod-1",
            "Contains 매칭",
            null,
            "test",
            "medium",
            null,
            List.of(),
            List.of(),
            List.of(),
            Map.of("category", "authentication"),
            Map.of(
                "selection",
                Map.of("TargetUserName|contains", "admin"),
                "condition",
                "selection"),
            List.of(),
            "raw",
            now);
    var result = mapper.map(sigma, tenantId, now);
    // contains 는 equals-like 로 인정 → unsupported 에 modifier 메시지 없어야.
    assertThat(result.unsupported()).noneMatch(s -> s.contains("field modifier"));
  }

  @Test
  void regex_modifier_는_unsupported() {
    var sigma =
        new SigmaRule(
            "mod-2",
            "Regex 매칭",
            null,
            "test",
            "medium",
            null,
            List.of(),
            List.of(),
            List.of(),
            Map.of("category", "authentication"),
            Map.of(
                "selection",
                Map.of("CommandLine|re", ".*evil.*"),
                "condition",
                "selection"),
            List.of(),
            "raw",
            now);
    var result = mapper.map(sigma, tenantId, now);
    assertThat(result.unsupported()).anySatisfy(s -> assertThat(s).contains("field modifier"));
  }

  @Test
  void source_ip_groupBy_default() {
    var sigma = baseSigma("g-1", "high");
    var result = mapper.map(sigma, tenantId, now);
    assertThat(result.rule().groupByField()).isEqualTo("source.ip");
  }

  @Test
  void description_에_sigma_id_와_refs_포함() {
    var sigma =
        new SigmaRule(
            "f1f8c8ee-3a3e-4b3f-9c2d-2b6c8e1f0a01",
            "Test",
            "원본 description",
            "test",
            "medium",
            null,
            List.of("https://example.com/ref1"),
            List.of("attack.t1110"),
            List.of(),
            Map.of("category", "authentication"),
            Map.of("selection", Map.of("EventID", 4625), "condition", "selection"),
            List.of(),
            "raw",
            now);
    var result = mapper.map(sigma, tenantId, now);
    assertThat(result.rule().description())
        .contains("원본 description")
        .contains("sigma_id: f1f8c8ee-3a3e-4b3f-9c2d-2b6c8e1f0a01")
        .contains("attack.t1110");
  }

  private SigmaRule baseSigma(String id, String level) {
    return new SigmaRule(
        id,
        "Base " + id,
        "desc",
        "test",
        level,
        null,
        List.of(),
        List.of(),
        List.of(),
        Map.of("category", "authentication"),
        Map.of("selection", Map.of("EventID", 4625), "condition", "selection"),
        List.of(),
        "raw",
        now);
  }
}
