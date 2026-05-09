package com.example.security.adapter.out.jpa.repository;

import com.example.security.adapter.out.jpa.entity.AlertEntity;
import com.example.security.domain.rule.Alert.AlertStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertJpaRepository extends JpaRepository<AlertEntity, UUID> {

  @Query(
      """
      SELECT a FROM AlertEntity a
      WHERE a.tenantId = :tenantId
        AND (:status IS NULL OR a.status = :status)
        AND (:from IS NULL OR a.firedAt >= :from)
        AND (:to IS NULL OR a.firedAt < :to)
      ORDER BY a.firedAt DESC, a.alertId DESC
      """)
  List<AlertEntity> findByFilters(
      @Param("tenantId") String tenantId,
      @Param("status") AlertStatus status,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}
