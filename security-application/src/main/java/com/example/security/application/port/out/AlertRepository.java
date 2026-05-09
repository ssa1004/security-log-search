package com.example.security.application.port.out;

import com.example.security.application.port.in.ListAlertsUseCase.ListAlertsQuery;
import com.example.security.domain.rule.Alert;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 발화된 알람 영속 — Postgres alerts 테이블. */
public interface AlertRepository {

  Alert save(Alert alert);

  Optional<Alert> findById(UUID alertId);

  List<Alert> query(ListAlertsQuery query);
}
