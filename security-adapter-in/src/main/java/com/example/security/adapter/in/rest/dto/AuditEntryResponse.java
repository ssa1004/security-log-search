package com.example.security.adapter.in.rest.dto;

import com.example.security.domain.audit.AuditEntry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEntryResponse(
    UUID entryId,
    String tenantId,
    Instant occurredAt,
    String actor,
    String actorRole,
    String action,
    String targetType,
    String targetId,
    String sourceIp,
    Map<String, String> details) {

  public static AuditEntryResponse from(AuditEntry e) {
    return new AuditEntryResponse(
        e.entryId(),
        e.tenantId().value(),
        e.occurredAt(),
        e.actor(),
        e.actorRole(),
        e.action().name(),
        e.targetType(),
        e.targetId(),
        e.sourceIp(),
        e.details());
  }
}
