package com.example.security.adapter.out.jpa.entity

import com.example.security.domain.audit.AuditEntry
import com.example.security.domain.audit.AuditEntry.AuditAction
import com.example.security.domain.common.TenantId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.LinkedHashMap
import java.util.UUID

/** audit_entries — append-only. */
@Entity
@Table(
    name = "audit_entries",
    indexes = [
        Index(name = "ix_audit_tenant_occurred", columnList = "tenant_id,occurred_at"),
        Index(name = "ix_audit_actor", columnList = "actor"),
        Index(name = "ix_audit_action", columnList = "action"),
    ],
)
class AuditEntryEntity {

    @Id
    @Column(name = "entry_id", nullable = false)
    private var entryId: UUID = UUID(0, 0)

    @Column(name = "tenant_id", nullable = false, length = 32)
    private var tenantId: String = ""

    @Column(name = "occurred_at", nullable = false)
    private var occurredAt: Instant = Instant.EPOCH

    @Column(nullable = false, length = 200)
    private var actor: String = ""

    @Column(name = "actor_role", length = 200)
    private var actorRole: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private var action: AuditAction = AuditAction.INGEST

    @Column(name = "target_type", length = 64)
    private var targetType: String? = null

    @Column(name = "target_id", length = 256)
    private var targetId: String? = null

    @Column(name = "source_ip", length = 64)
    private var sourceIp: String? = null

    /** 단순 key=value 콤마 직렬화 — 분석은 안 하고 fetch 시에만 풀어 본다. */
    @Column(length = 4000)
    private var details: String? = null

    fun toDomain(): AuditEntry =
        AuditEntry(
            entryId,
            TenantId.of(tenantId),
            occurredAt,
            actor,
            actorRole,
            action,
            targetType,
            targetId,
            sourceIp,
            deserialize(details),
        )

    companion object {
        @JvmStatic
        fun from(entry: AuditEntry): AuditEntryEntity = AuditEntryEntity().apply {
            entryId = entry.entryId
            tenantId = entry.tenantId.value
            occurredAt = entry.occurredAt
            actor = entry.actor
            actorRole = entry.actorRole
            action = entry.action
            targetType = entry.targetType
            targetId = entry.targetId
            sourceIp = entry.sourceIp
            details = serialize(entry.details)
        }

        @JvmStatic
        internal fun serialize(details: Map<String, String>?): String {
            if (details.isNullOrEmpty()) return ""
            val sb = StringBuilder()
            for ((k, v) in details) {
                if (sb.isNotEmpty()) sb.append(';')
                sb.append(escape(k)).append('=').append(escape(v))
            }
            return sb.toString()
        }

        @JvmStatic
        internal fun deserialize(s: String?): Map<String, String> {
            if (s.isNullOrBlank()) return emptyMap()
            val m = LinkedHashMap<String, String>()
            // ; 와 = 를 직접 escape-aware 하게 한 char 씩 파싱.
            val key = StringBuilder()
            val value = StringBuilder()
            var inValue = false
            var esc = false
            for (c in s) {
                val target = if (inValue) value else key
                when {
                    esc -> {
                        target.append(c)
                        esc = false
                    }
                    c == '\\' -> esc = true
                    c == '=' && !inValue -> inValue = true
                    c == ';' -> {
                        if (inValue) {
                            m[key.toString()] = value.toString()
                        }
                        key.setLength(0)
                        value.setLength(0)
                        inValue = false
                    }
                    else -> target.append(c)
                }
            }
            if (inValue) {
                m[key.toString()] = value.toString()
            }
            return m
        }

        private fun escape(v: String): String =
            v.replace("\\", "\\\\").replace(";", "\\;").replace("=", "\\=")
    }
}
