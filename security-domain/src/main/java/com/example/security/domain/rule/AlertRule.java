package com.example.security.domain.rule;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.LogEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 알람 룰 — 운영자가 정의하는 상관분석 규칙.
 *
 * <p>본 시스템에서 알람 룰은 두 종류로 분류된다.
 *
 * <ul>
 *   <li>{@link RuleType#THRESHOLD} — "같은 IP 에서 5분 안 5회 인증 실패" 같이 특정 그룹 키
 *       기준 카운트가 임계값을 넘으면 발화.
 *   <li>{@link RuleType#SEQUENCE} — "5회 실패 직후 1회 성공" 같이 두 단계 시퀀스 매칭. brute-
 *       force 침입 패턴이 대표 예.
 * </ul>
 *
 * <p>룰 평가는 Flink job 의 {@code KeyedProcessFunction} 이 담당하고, 룰 자체는 PostgreSQL 에
 * 영속된다. 운영자가 룰 추가 / 수정 시 Flink 의 broadcast state 로 hot reload 된다 (Flink job
 * 재시작 불필요).
 */
public record AlertRule(
    UUID ruleId,
    TenantId tenantId,
    String name,
    String description,
    RuleType type,
    /** ECS event.category 필터 (예: "authentication"). null 이면 전체 매칭. */
    String filterCategory,
    /** ECS event.action 필터 (예: "logon"). null 이면 전체. */
    String filterAction,
    /** ECS event.outcome 필터 (예: "failure"). null 이면 전체. */
    String filterOutcome,
    /** 그룹 키 — "source.ip" / "user.name" / "host.hostname" 등. */
    String groupByField,
    /** 임계값 — THRESHOLD 룰의 카운트 임계값. */
    int threshold,
    /** 슬라이딩 윈도우 길이 — 5분 / 1시간 등. */
    Duration window,
    /** 발화 시 alert.severity. */
    Severity severity,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt) {

  public AlertRule {
    Objects.requireNonNull(ruleId);
    Objects.requireNonNull(tenantId);
    Objects.requireNonNull(name);
    Objects.requireNonNull(type);
    Objects.requireNonNull(groupByField);
    Objects.requireNonNull(window);
    Objects.requireNonNull(severity);
    Objects.requireNonNull(createdAt);
    Objects.requireNonNull(updatedAt);
    if (threshold < 1) {
      throw new IllegalArgumentException("threshold 는 1 이상: " + threshold);
    }
    if (window.isNegative() || window.isZero()) {
      throw new IllegalArgumentException("window 는 양수: " + window);
    }
    if (window.compareTo(Duration.ofDays(1)) > 0) {
      // 1일을 초과하는 윈도우는 streaming state 폭증 위험 → 별도 batch 잡으로 가야 함.
      throw new IllegalArgumentException("window 는 1일 이하: " + window);
    }
  }

  /** 이벤트가 본 룰의 필터에 매칭되는지. */
  public boolean matches(LogEvent event) {
    if (!enabled) return false;
    if (!event.tenantId().equals(tenantId)) return false;
    if (filterCategory != null && !filterCategory.equals(event.eventCategory())) return false;
    if (filterAction != null && !filterAction.equals(event.eventAction())) return false;
    if (filterOutcome != null && !filterOutcome.equals(event.eventOutcome())) return false;
    return true;
  }

  /** 그룹 키 값 추출 — Flink keyBy 의 키. */
  public String extractGroupKey(LogEvent event) {
    return switch (groupByField) {
      case "source.ip" -> nullSafe(event.sourceIp());
      case "destination.ip" -> nullSafe(event.destinationIp());
      case "user.name" -> nullSafe(event.userName());
      case "host.hostname" -> nullSafe(event.hostName());
      case "tenant" -> tenantId.value();
      default -> nullSafe(event.labels().get(groupByField));
    };
  }

  private static String nullSafe(String s) {
    return s == null ? "<unknown>" : s;
  }

  public enum RuleType {
    /** 같은 그룹 키에서 윈도우 안 카운트 임계값 초과. */
    THRESHOLD,
    /** 그룹 키 안에서 N회 실패 직후 1회 성공 — brute-force 패턴. */
    SEQUENCE
  }
}
