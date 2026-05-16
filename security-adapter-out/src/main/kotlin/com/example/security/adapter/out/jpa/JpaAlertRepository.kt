package com.example.security.adapter.out.jpa

import com.example.security.adapter.out.jpa.entity.AlertEntity
import com.example.security.adapter.out.jpa.repository.AlertJpaRepository
import com.example.security.application.port.`in`.ListAlertsUseCase.ListAlertsQuery
import com.example.security.application.port.out.AlertRepository
import com.example.security.domain.rule.Alert
import java.util.Optional
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class JpaAlertRepository(
    private val jpa: AlertJpaRepository,
) : AlertRepository {

    override fun save(alert: Alert): Alert = jpa.save(AlertEntity.from(alert)).toDomain()

    @Transactional(readOnly = true)
    override fun findById(alertId: UUID): Optional<Alert> =
        jpa.findById(alertId).map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun query(query: ListAlertsQuery): List<Alert> {
        val pageable = PageRequest.of(0, query.size)
        return jpa
            .findByFilters(
                query.tenantId.value,
                query.status.orElse(null),
                query.from.orElse(null),
                query.to.orElse(null),
                pageable,
            )
            .map { it.toDomain() }
    }
}
