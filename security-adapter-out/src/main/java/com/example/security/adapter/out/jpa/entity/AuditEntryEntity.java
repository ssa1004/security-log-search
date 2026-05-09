package com.example.security.adapter.out.jpa.entity;

import com.example.security.domain.audit.AuditEntry;
import com.example.security.domain.audit.AuditEntry.AuditAction;
import com.example.security.domain.common.TenantId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** audit_entries — append-only. */
@Entity
@Table(
    name = "audit_entries",
    indexes = {
      @Index(name = "ix_audit_tenant_occurred", columnList = "tenant_id,occurred_at"),
      @Index(name = "ix_audit_actor", columnList = "actor"),
      @Index(name = "ix_audit_action", columnList = "action")
    })
public class AuditEntryEntity {

  @Id
  @Column(name = "entry_id", nullable = false)
  private UUID entryId;

  @Column(name = "tenant_id", nullable = false, length = 32)
  private String tenantId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(nullable = false, length = 200)
  private String actor;

  @Column(name = "actor_role", length = 200)
  private String actorRole;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AuditAction action;

  @Column(name = "target_type", length = 64)
  private String targetType;

  @Column(name = "target_id", length = 256)
  private String targetId;

  @Column(name = "source_ip", length = 64)
  private String sourceIp;

  /** 단순 key=value 콤마 직렬화 — 분석은 안 하고 fetch 시에만 풀어 본다. */
  @Column(length = 4000)
  private String details;

  protected AuditEntryEntity() {}

  public static AuditEntryEntity from(AuditEntry entry) {
    var e = new AuditEntryEntity();
    e.entryId = entry.entryId();
    e.tenantId = entry.tenantId().value();
    e.occurredAt = entry.occurredAt();
    e.actor = entry.actor();
    e.actorRole = entry.actorRole();
    e.action = entry.action();
    e.targetType = entry.targetType();
    e.targetId = entry.targetId();
    e.sourceIp = entry.sourceIp();
    e.details = serialize(entry.details());
    return e;
  }

  public AuditEntry toDomain() {
    return new AuditEntry(
        entryId,
        TenantId.of(tenantId),
        occurredAt,
        actor,
        actorRole,
        action,
        targetType,
        targetId,
        sourceIp,
        deserialize(details));
  }

  static String serialize(Map<String, String> details) {
    if (details == null || details.isEmpty()) return "";
    var sb = new StringBuilder();
    for (var e : details.entrySet()) {
      if (sb.length() > 0) sb.append(';');
      sb.append(escape(e.getKey())).append('=').append(escape(e.getValue() == null ? "" : e.getValue()));
    }
    return sb.toString();
  }

  static Map<String, String> deserialize(String s) {
    if (s == null || s.isBlank()) return Map.of();
    Map<String, String> m = new LinkedHashMap<>();
    // ; 와 = 를 직접 escape-aware 하게 한 char 씩 파싱.
    var key = new StringBuilder();
    var value = new StringBuilder();
    boolean inValue = false;
    boolean esc = false;
    for (int i = 0; i < s.length(); i++) {
      var c = s.charAt(i);
      var target = inValue ? value : key;
      if (esc) {
        target.append(c);
        esc = false;
      } else if (c == '\\') {
        esc = true;
      } else if (c == '=' && !inValue) {
        inValue = true;
      } else if (c == ';') {
        if (inValue) {
          m.put(key.toString(), value.toString());
        }
        key.setLength(0);
        value.setLength(0);
        inValue = false;
      } else {
        target.append(c);
      }
    }
    if (inValue) {
      m.put(key.toString(), value.toString());
    }
    return m;
  }

  private static String escape(String v) {
    return v.replace("\\", "\\\\").replace(";", "\\;").replace("=", "\\=");
  }

  // 사용되지 않는 import 제거를 위해.
  @SuppressWarnings("unused")
  private static void unused() {
    new HashMap<String, String>();
    Arrays.asList();
  }
}
