package com.example.security.adapter.out.jpa;

import com.example.security.adapter.out.jpa.entity.AlertEntity;
import com.example.security.adapter.out.jpa.repository.AlertJpaRepository;
import com.example.security.application.port.in.ListAlertsUseCase.ListAlertsQuery;
import com.example.security.application.port.out.AlertRepository;
import com.example.security.domain.rule.Alert;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class JpaAlertRepository implements AlertRepository {

  private final AlertJpaRepository jpa;

  public JpaAlertRepository(AlertJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Alert save(Alert alert) {
    return jpa.save(AlertEntity.from(alert)).toDomain();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Alert> findById(UUID alertId) {
    return jpa.findById(alertId).map(AlertEntity::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Alert> query(ListAlertsQuery q) {
    var pageable = PageRequest.of(0, q.size());
    return jpa
        .findByFilters(
            q.tenantId().value(),
            q.status().orElse(null),
            q.from().orElse(null),
            q.to().orElse(null),
            pageable)
        .stream()
        .map(AlertEntity::toDomain)
        .toList();
  }
}
