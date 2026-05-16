package com.example.security.application.service

import com.example.security.application.port.`in`.EvaluateAlertUseCase
import com.example.security.application.port.out.AlertNotificationPort
import com.example.security.application.port.out.AlertRepository
import com.example.security.application.port.out.AuditLogPort
import com.example.security.domain.audit.AuditEntry
import com.example.security.domain.rule.Alert
import java.time.Clock
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * use case 5 — Flink 가 발화한 알람 처리.
 *
 * 외부 통보 호출은 fire-and-forget — 실패해도 알람 자체는 정상 저장된다.
 */
@Service
open class EvaluateAlertService(
    private val alerts: AlertRepository,
    private val notification: AlertNotificationPort,
    private val audit: AuditLogPort,
    private val clock: Clock,
) : EvaluateAlertUseCase {

    @Transactional
    override fun handleFired(alert: Alert): Alert {
        val saved = alerts.save(alert)
        audit.append(
            AuditEntry(
                UUID.randomUUID(),
                saved.tenantId,
                clock.instant(),
                "flink-job",
                "system",
                AuditEntry.AuditAction.ALERT_FIRED,
                "alert",
                saved.alertId.toString(),
                null,
                // groupKey 는 도메인상 보통 set 되지만 nullable — Java 의 Map.of NPE 보다
                // empty string fallback 이 audit 자체 실패 위험을 줄인다.
                mapOf(
                    "rule" to saved.ruleName,
                    "severity" to saved.severity.name,
                    "matched" to saved.matchedCount.toString(),
                    "groupKey" to (saved.groupKey ?: ""),
                ),
            )
        )

        try {
            notification.notify(saved)
        } catch (e: RuntimeException) {
            log.warn("notification 실패 — 알람은 정상 저장: alertId={}", saved.alertId, e)
        }
        return saved
    }

    companion object {
        private val log = LoggerFactory.getLogger(EvaluateAlertService::class.java)
    }
}
