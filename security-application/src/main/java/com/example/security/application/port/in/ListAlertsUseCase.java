package com.example.security.application.port.in;

import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.Alert;
import com.example.security.domain.rule.Alert.AlertStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * use case 6 — 운영자가 발화된 알람 timeline 조회.
 *
 * <p>tenantId 강제, status 필터, 기간 필터, cursor pagination.
 */
public interface ListAlertsUseCase {

  Page list(ListAlertsQuery query, OperatorContext operator);

  Alert acknowledge(UUID alertId, OperatorContext operator);

  Alert resolve(UUID alertId, OperatorContext operator);

  Alert markFalsePositive(UUID alertId, OperatorContext operator);

  record ListAlertsQuery(
      TenantId tenantId,
      Optional<AlertStatus> status,
      Optional<Instant> from,
      Optional<Instant> to,
      int size,
      Optional<UUID> after) {

    /** 알람 timeline 한 페이지 상한 — UI 가 cursor 로 추가 페이지 요청. (API4 — 자원 소비 제한) */
    public static final int MAX_SIZE = 500;

    public ListAlertsQuery {
      if (size < 1 || size > MAX_SIZE) {
        throw new IllegalArgumentException("size 는 1~" + MAX_SIZE + ": " + size);
      }
    }
  }

  record Page(List<Alert> alerts, UUID nextCursor) {}
}
