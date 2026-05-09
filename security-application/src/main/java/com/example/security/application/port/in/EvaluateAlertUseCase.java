package com.example.security.application.port.in;

import com.example.security.domain.rule.Alert;

/**
 * use case 5 — 발화된 알람 처리. Flink job 이 Kafka {@code alerts.fired} 로 보낸 메시지를
 * Spring 측 consumer 가 받아 본 use case 를 호출한다.
 *
 * <p>처리 단계:
 *
 * <ol>
 *   <li>{@code alerts} 테이블에 INSERT
 *   <li>audit_entries 에 알람 발화 기록
 *   <li>(optional) notification-hub 외부 호출
 * </ol>
 */
public interface EvaluateAlertUseCase {

  Alert handleFired(Alert alert);
}
