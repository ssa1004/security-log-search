package com.example.security.adapter.out.notification;

import com.example.security.application.port.out.AlertNotificationPort;
import com.example.security.domain.rule.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * notification-hub 같은 외부 시스템과 연동되지 않은 환경 (local / dev) 의 기본 구현.
 *
 * <p>로그만 남긴다. prod 환경은 별도 외부 호출 어댑터가 본 빈을 대체 — application.yml 의
 * {@code security.notification.external=true} 시 본 빈이 비활성화되고 다른 구현이 활성화되도록 한다.
 */
@Component
@ConditionalOnProperty(
    name = "security.notification.external",
    havingValue = "false",
    matchIfMissing = true)
public class NoOpAlertNotification implements AlertNotificationPort {

  private static final Logger log = LoggerFactory.getLogger(NoOpAlertNotification.class);

  @Override
  public void notify(Alert alert) {
    log.info(
        "alert fired (no external notification configured): id={} severity={} rule={} group={}",
        alert.alertId(),
        alert.severity(),
        alert.ruleName(),
        alert.groupKey());
  }
}
