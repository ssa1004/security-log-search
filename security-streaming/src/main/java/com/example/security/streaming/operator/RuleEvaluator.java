package com.example.security.streaming.operator;

import com.example.security.domain.event.LogEvent;
import com.example.security.domain.rule.Alert;
import com.example.security.domain.rule.Alert.AlertStatus;
import com.example.security.domain.rule.AlertRule;
import com.example.security.domain.rule.AlertRule.RuleType;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Flink {@link KeyedProcessFunction} 의 알맹이를 단위 테스트 가능한 형태로 분리한 평가 엔진.
 *
 * <p>본 클래스는 deterministic — 같은 룰 + 같은 이벤트 시퀀스를 넣으면 같은 알람이 나온다.
 * Flink runtime 이 없는 단위 테스트에서 핵심 로직만 검증할 수 있다.
 *
 * <p>알고리즘:
 *
 * <ul>
 *   <li>같은 룰 + 같은 그룹 키 안에서 슬라이딩 윈도우 (이벤트 도착 시각 기준)
 *   <li>윈도우 안 카운트가 룰의 threshold 도달하면:
 *       <ul>
 *         <li>THRESHOLD: 즉시 알람
 *         <li>SEQUENCE: 다음 직후 1회 성공 이벤트가 같은 그룹 키에서 들어오면 알람
 *       </ul>
 *   <li>윈도우보다 옛 이벤트는 evict
 * </ul>
 */
public class RuleEvaluator {

  /** 그룹 키 (예: 192.168.1.10) 단위로 카운트. */
  private final Deque<EventEntry> window = new LinkedList<>();

  /** SEQUENCE 룰: 가장 최근 threshold 도달 시각 (성공 이벤트 도착 시 알람 생성). */
  private java.time.Instant thresholdReachedAt;

  private final AlertRule rule;
  private final String groupKey;
  private final Supplier<UUID> alertIdSupplier;

  public RuleEvaluator(AlertRule rule, String groupKey, Supplier<UUID> alertIdSupplier) {
    this.rule = rule;
    this.groupKey = groupKey;
    this.alertIdSupplier = alertIdSupplier;
  }

  /**
   * 새 이벤트 처리. 알람이 발화되면 결과를 반환, 아니면 빈 list.
   *
   * <p>이벤트는 룰의 filter 에 매칭되었거나 (THRESHOLD), 또는 SEQUENCE 의 trailing success 도
   * 본 메서드로 들어와야 한다 — 호출 측이 책임.
   */
  public List<Alert> onEvent(LogEvent event) {
    var now = event.timestamp();
    evictOlderThan(now.minus(rule.window()));

    var alerts = new ArrayList<Alert>();

    if (rule.matches(event)) {
      window.add(new EventEntry(event.eventId(), now));
      if (window.size() >= rule.threshold()) {
        if (rule.type() == RuleType.THRESHOLD) {
          alerts.add(buildAlert(now, snapshotIds(), "임계값 초과"));
        } else if (rule.type() == RuleType.SEQUENCE) {
          // trailing success 가 도착할 때까지 시각만 보관.
          thresholdReachedAt = now;
        }
      }
    } else if (rule.type() == RuleType.SEQUENCE
        && thresholdReachedAt != null
        && isTrailingSuccess(event)) {
      // threshold 도달 후 윈도우 안 trailing success → 알람.
      var dt = java.time.Duration.between(thresholdReachedAt, now);
      if (!dt.isNegative() && dt.compareTo(rule.window()) <= 0) {
        var ids = snapshotIds();
        ids.add(event.eventId());
        alerts.add(buildAlert(now, ids, "실패 시퀀스 직후 성공 (brute-force 의심)"));
        thresholdReachedAt = null;
        window.clear();
      }
    }

    return alerts;
  }

  /** Flink Timer 가 윈도우 만료 시 호출 — state 정리만. */
  public void onTimer(java.time.Instant now) {
    evictOlderThan(now.minus(rule.window()));
    if (window.isEmpty()) {
      thresholdReachedAt = null;
    }
  }

  /** 같은 룰의 그룹 키 안에서 trailing success 판정 — 룰의 그룹 키 추출과 동일하면 매칭. */
  private boolean isTrailingSuccess(LogEvent event) {
    if (!event.tenantId().equals(rule.tenantId())) return false;
    if (rule.filterCategory() != null
        && !rule.filterCategory().equals(event.eventCategory())) return false;
    if (rule.filterAction() != null && !rule.filterAction().equals(event.eventAction())) return false;
    if (!"success".equals(event.eventOutcome())) return false;
    return groupKey.equals(rule.extractGroupKey(event));
  }

  private void evictOlderThan(java.time.Instant cutoff) {
    while (!window.isEmpty() && window.peekFirst().timestamp.isBefore(cutoff)) {
      window.pollFirst();
    }
  }

  private List<UUID> snapshotIds() {
    var out = new ArrayList<UUID>(window.size());
    for (var e : window) out.add(e.eventId);
    return out;
  }

  private Alert buildAlert(java.time.Instant firedAt, List<UUID> ids, String message) {
    return new Alert(
        alertIdSupplier.get(),
        rule.tenantId(),
        rule.ruleId(),
        rule.name(),
        rule.severity(),
        groupKey,
        rule.groupByField(),
        ids.size(),
        firedAt.minus(rule.window()),
        firedAt,
        firedAt,
        AlertStatus.OPEN,
        ids,
        message);
  }

  /** 단위 테스트용 — 현재 윈도우 사이즈. */
  public int windowSize() {
    return window.size();
  }

  /** 내부 entry. */
  record EventEntry(UUID eventId, java.time.Instant timestamp) {}
}
