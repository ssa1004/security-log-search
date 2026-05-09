package com.example.security.domain.event;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 정규화된 보안 로그 이벤트 — ECS (Elastic Common Schema) 8.x 를 1차 모델로 한 도메인 객체.
 *
 * <p>ECS 의 핵심 필드들을 record 로 평탄화했다. raw event 는 다양한 source (방화벽 / EDR /
 * 시스템 / 응용) 에서 오지만 본 객체로 정규화된 후에는 일관된 검색 / 집계가 가능하다.
 *
 * <p>OCSF (Open Cybersecurity Schema Framework) 매핑은 별도 mapper 가 담당한다 — 본 도메인은
 * ECS 필드 이름 (snake_case) 을 따른다.
 *
 * <p>세부 필드 그룹 (ECS spec 기준):
 *
 * <ul>
 *   <li>{@code event.*} — kind, category, type, action, outcome, severity
 *   <li>{@code source.*} / {@code destination.*} — IP, port, address
 *   <li>{@code user.*} — name, id, domain
 *   <li>{@code host.*} — hostname, os
 *   <li>{@code labels} — 자유 형식 key-value (raw 에서 정규화 안 된 항목)
 * </ul>
 */
@SuppressWarnings("ClassCanBeRecord")
public record LogEvent(
    /** 이벤트 식별자. idempotency 키이자 OpenSearch _id, ClickHouse event_id 로 사용. */
    UUID eventId,
    /** 테넌트. */
    TenantId tenantId,
    /** 이벤트 발생 시각 (source 가 보고한 시각). */
    Instant timestamp,
    /** 시스템에 수집된 시각 — `@timestamp` 와 별도로 audit 에 사용. */
    Instant ingestedAt,
    /** ECS event.kind — event / alert / metric / state / signal */
    String eventKind,
    /** ECS event.category — authentication / network / process / file / web 등 */
    String eventCategory,
    /** ECS event.type — start / end / info / change / denied / allowed 등 */
    String eventType,
    /** ECS event.action — login / logout / connection_dropped 등 도메인 동사. */
    String eventAction,
    /** ECS event.outcome — success / failure / unknown */
    String eventOutcome,
    /** 5단계 심각도 (ECS event.severity 매핑). */
    Severity severity,
    /** ECS source.ip. */
    String sourceIp,
    /** ECS source.port. */
    Integer sourcePort,
    /** ECS destination.ip. */
    String destinationIp,
    /** ECS destination.port. */
    Integer destinationPort,
    /** ECS user.name (또는 id, login). */
    String userName,
    /** ECS host.hostname. */
    String hostName,
    /** ECS host.os.name (linux / windows / macos / ios / android 등). */
    String hostOs,
    /** ECS message — 사람이 읽는 한 줄 요약. */
    String message,
    /** 자유형 라벨 — 정규화 안 된 raw 필드 보관. */
    Map<String, String> labels) implements java.io.Serializable {

  public LogEvent {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(timestamp, "timestamp");
    Objects.requireNonNull(ingestedAt, "ingestedAt");
    Objects.requireNonNull(severity, "severity");
    if (timestamp.isAfter(ingestedAt.plusSeconds(60))) {
      // source 가 미래 시각을 보낸 경우 — clock skew 허용 60초.
      throw new IllegalArgumentException(
          "이벤트 timestamp 가 ingestedAt + 60s 보다 미래: " + timestamp + " vs " + ingestedAt);
    }
    labels = labels == null ? Map.of() : Map.copyOf(labels);
  }

  /** OpenSearch index 이름. {@code events-{tenant}-{yyyy.MM.dd}} 패턴. */
  public String openSearchIndexName() {
    var date = timestamp.atZone(java.time.ZoneOffset.UTC).toLocalDate();
    return "events-%s-%04d.%02d.%02d"
        .formatted(tenantId.value(), date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }

  /** OpenSearch write alias — 인덱스 직접 쓰지 않고 alias 로 추상화. */
  public String openSearchWriteAlias() {
    return "events-%s-write".formatted(tenantId.value());
  }

  /** 인증 실패 이벤트 여부 — Flink correlation rule 의 default 한 가지. */
  public boolean isAuthFailure() {
    return "authentication".equals(eventCategory)
        && "failure".equals(eventOutcome);
  }

  /** 인증 성공 이벤트 여부. */
  public boolean isAuthSuccess() {
    return "authentication".equals(eventCategory)
        && "success".equals(eventOutcome);
  }
}
