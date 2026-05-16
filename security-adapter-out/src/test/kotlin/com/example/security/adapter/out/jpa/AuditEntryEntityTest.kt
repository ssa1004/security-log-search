package com.example.security.adapter.out.jpa

import com.example.security.adapter.out.jpa.entity.AuditEntryEntity
import com.example.security.domain.audit.AuditEntry
import com.example.security.domain.audit.AuditEntry.AuditAction
import com.example.security.domain.common.TenantId
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuditEntryEntityTest {

    @Test
    fun `domain to entity round trip`() {
        val details = linkedMapOf(
            "query" to "user.name:alice",
            "returned" to "42",
        )
        val entry = AuditEntry(
            UUID.randomUUID(),
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "alice",
            "OPERATOR",
            AuditAction.SEARCH,
            "search",
            "user.name:alice",
            "10.0.0.1",
            details,
        )

        val roundTripped = AuditEntryEntity.from(entry).toDomain()

        assertThat(roundTripped.entryId).isEqualTo(entry.entryId)
        assertThat(roundTripped.actor).isEqualTo("alice")
        assertThat(roundTripped.details).containsEntry("query", "user.name:alice")
        assertThat(roundTripped.details).containsEntry("returned", "42")
    }

    @Test
    fun `details 특수문자 이스케이프`() {
        val details = linkedMapOf(
            "filter" to "user=admin;role=ADMIN",
            "escape" to "back\\slash",
        )
        val entry = AuditEntry(
            UUID.randomUUID(),
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "alice",
            "OPERATOR",
            AuditAction.SEARCH,
            "search",
            "x",
            "10.0.0.1",
            details,
        )

        val roundTripped = AuditEntryEntity.from(entry).toDomain()

        assertThat(roundTripped.details).containsEntry("filter", "user=admin;role=ADMIN")
        assertThat(roundTripped.details).containsEntry("escape", "back\\slash")
    }

    @Test
    fun `빈 details 도 정상 round trip`() {
        val entry = AuditEntry(
            UUID.randomUUID(),
            TenantId.of("acme"),
            Instant.parse("2026-05-09T12:00:00Z"),
            "alice",
            "OPERATOR",
            AuditAction.SEARCH,
            "search",
            "x",
            "10.0.0.1",
            emptyMap(),
        )

        val roundTripped = AuditEntryEntity.from(entry).toDomain()

        assertThat(roundTripped.details).isEmpty()
    }
}
