package com.example.security.adapter.out.jpa;

import com.example.security.adapter.out.jpa.entity.AuditEntryEntity;
import com.example.security.adapter.out.jpa.repository.AuditJpaRepository;
import com.example.security.application.port.in.QueryAuditLogUseCase.AuditQuery;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.domain.audit.AuditEntry;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * audit_entries — append-only.
 *
 * <p>본 어댑터는 {@code @Transactional} 로 INSERT 만 하고 UPDATE/DELETE 는 JpaRepository 에 노출
 * 안 한다. 보존 5년 (ISMS-P 권고) 후 별도 batch 로 archive cold storage 이관.
 */
@Component
@Transactional
public class JpaAuditLogAdapter implements AuditLogPort {

  private final AuditJpaRepository jpa;

  public JpaAuditLogAdapter(AuditJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public void append(AuditEntry entry) {
    jpa.save(AuditEntryEntity.from(entry));
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditEntry> query(AuditQuery q) {
    var pageable = PageRequest.of(0, q.size());
    return jpa
        .findByFilters(
            q.tenantId().value(),
            q.actor().orElse(null),
            q.action().orElse(null),
            q.from().orElse(null),
            q.to().orElse(null),
            pageable)
        .stream()
        .map(AuditEntryEntity::toDomain)
        .toList();
  }
}
