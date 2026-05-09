package com.example.security.domain.sigma;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sigma 룰 — vendor 중립 SIEM 룰 표준.
 *
 * <p>Sigma 는 SigmaHQ 가 정의한 YAML 기반 SIEM 탐지 룰 포맷이다 (OSS, MIT 라이선스).
 * 외부 위협 인텔리전스에서 배포되는 룰을 가져와 본 시스템의 {@link
 * com.example.security.domain.rule.AlertRule} 로 변환할 수 있다.
 *
 * <p>본 record 는 Sigma YAML 의 핵심 필드를 담는 표현이다. 변환되지 않은 detection 본문은 {@code
 * detection} 필드에 raw map 으로 보관한다 ({@link com.example.security.domain.sigma.SigmaToAlertRuleMapper}
 * 가 사용).
 *
 * <p>Sigma spec: <a
 * href="https://github.com/SigmaHQ/sigma-specification">github.com/SigmaHQ/sigma-specification</a>.
 */
public record SigmaRule(
    /** Sigma {@code id} — UUID. */
    String id,
    /** Sigma {@code title}. */
    String title,
    /** Sigma {@code description}. */
    String description,
    /** Sigma {@code status} — stable / test / experimental / deprecated / unsupported. */
    String status,
    /** Sigma {@code level} — informational / low / medium / high / critical. */
    String level,
    /** Sigma {@code author}. */
    String author,
    /** Sigma {@code references} — URL 목록. */
    List<String> references,
    /** Sigma {@code tags} — MITRE ATT&CK 등 (예: attack.t1110). */
    List<String> tags,
    /** Sigma {@code falsepositives} — false positive 시나리오. */
    List<String> falsepositives,
    /** Sigma {@code logsource} — category / product / service. */
    Map<String, String> logsource,
    /** Sigma {@code detection} — selection 들 + condition. raw map. */
    Map<String, Object> detection,
    /** Sigma {@code fields} — 운영자가 검색 결과에서 보고 싶어하는 필드. */
    List<String> fields,
    /** Sigma YAML 원본 (감사 / 재변환용 보관). */
    String source,
    /** import 한 시각. */
    Instant importedAt) implements java.io.Serializable {

  public SigmaRule {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(detection, "detection");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(importedAt, "importedAt");
    references = references == null ? List.of() : List.copyOf(references);
    tags = tags == null ? List.of() : List.copyOf(tags);
    falsepositives = falsepositives == null ? List.of() : List.copyOf(falsepositives);
    fields = fields == null ? List.of() : List.copyOf(fields);
    logsource = logsource == null ? Map.of() : Map.copyOf(logsource);
    detection = Map.copyOf(detection);
  }

  /** detection.condition 문자열 추출 — 없으면 null. */
  public String condition() {
    var cond = detection.get("condition");
    return cond == null ? null : cond.toString();
  }
}
