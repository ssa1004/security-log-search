package com.example.security.adapter.out.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.adapter.out.jpa.entity.AuditEntryEntity;
import com.example.security.domain.audit.AuditEntry;
import com.example.security.domain.audit.AuditEntry.AuditAction;
import com.example.security.domain.common.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEntryEntityTest {

  @Test
  void domain_to_entity_round_trip() {
    Map<String, String> details = new LinkedHashMap<>();
    details.put("query", "user.name:alice");
    details.put("returned", "42");
    var entry =
        new AuditEntry(
            UUID.randomUUID(),
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "alice",
            "OPERATOR",
            AuditAction.SEARCH,
            "search",
            "user.name:alice",
            "10.0.0.1",
            details);

    var roundTripped = AuditEntryEntity.from(entry).toDomain();

    assertThat(roundTripped.entryId()).isEqualTo(entry.entryId());
    assertThat(roundTripped.actor()).isEqualTo("alice");
    assertThat(roundTripped.details()).containsEntry("query", "user.name:alice");
    assertThat(roundTripped.details()).containsEntry("returned", "42");
  }

  @Test
  void details_특수문자_이스케이프() {
    Map<String, String> details = new LinkedHashMap<>();
    details.put("filter", "user=admin;role=ADMIN");
    details.put("escape", "back\\slash");
    var entry =
        new AuditEntry(
            UUID.randomUUID(),
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "alice",
            "OPERATOR",
            AuditAction.SEARCH,
            "search",
            "x",
            "10.0.0.1",
            details);

    var roundTripped = AuditEntryEntity.from(entry).toDomain();

    assertThat(roundTripped.details()).containsEntry("filter", "user=admin;role=ADMIN");
    assertThat(roundTripped.details()).containsEntry("escape", "back\\slash");
  }

  @Test
  void 빈_details_도_정상_round_trip() {
    var entry =
        new AuditEntry(
            UUID.randomUUID(),
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "alice",
            "OPERATOR",
            AuditAction.SEARCH,
            "search",
            "x",
            "10.0.0.1",
            Map.of());

    var roundTripped = AuditEntryEntity.from(entry).toDomain();

    assertThat(roundTripped.details()).isEmpty();
  }
}
