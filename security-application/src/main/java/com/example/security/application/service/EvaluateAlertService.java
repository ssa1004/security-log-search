package com.example.security.application.service;

import com.example.security.application.port.in.EvaluateAlertUseCase;
import com.example.security.application.port.out.AlertNotificationPort;
import com.example.security.application.port.out.AlertRepository;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.domain.audit.AuditEntry;
import com.example.security.domain.audit.AuditEntry.AuditAction;
import com.example.security.domain.rule.Alert;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * use case 5 — Flink 가 발화한 알람 처리.
 *
 * <p>외부 통보 호출은 fire-and-forget — 실패해도 알람 자체는 정상 저장된다.
 */
@Service
public class EvaluateAlertService implements EvaluateAlertUseCase {

  private static final Logger log = LoggerFactory.getLogger(EvaluateAlertService.class);

  private final AlertRepository alerts;
  private final AlertNotificationPort notification;
  private final AuditLogPort audit;
  private final Clock clock;

  public EvaluateAlertService(
      AlertRepository alerts,
      AlertNotificationPort notification,
      AuditLogPort audit,
      Clock clock) {
    this.alerts = alerts;
    this.notification = notification;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Alert handleFired(Alert alert) {
    var saved = alerts.save(alert);
    audit.append(
        new AuditEntry(
            UUID.randomUUID(),
            saved.tenantId(),
            clock.instant(),
            "flink-job",
            "system",
            AuditAction.ALERT_ACKNOWLEDGED, // dummy — 발화 자체는 시스템 동작
            "alert",
            saved.alertId().toString(),
            null,
            Map.of(
                "rule", saved.ruleName(),
                "severity", saved.severity().name(),
                "matched", Integer.toString(saved.matchedCount()),
                "groupKey", saved.groupKey())));

    try {
      notification.notify(saved);
    } catch (RuntimeException e) {
      log.warn("notification 실패 — 알람은 정상 저장: alertId={}", saved.alertId(), e);
    }
    return saved;
  }
}
