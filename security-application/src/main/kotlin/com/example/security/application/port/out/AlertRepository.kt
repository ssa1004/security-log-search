package com.example.security.application.port.out

import com.example.security.application.port.`in`.ListAlertsUseCase
import com.example.security.domain.rule.Alert
import java.util.Optional
import java.util.UUID

/** 발화된 알람 영속 — Postgres alerts 테이블. */
interface AlertRepository {

    fun save(alert: Alert): Alert

    fun findById(alertId: UUID): Optional<Alert>

    fun query(query: ListAlertsUseCase.ListAlertsQuery): List<Alert>
}
