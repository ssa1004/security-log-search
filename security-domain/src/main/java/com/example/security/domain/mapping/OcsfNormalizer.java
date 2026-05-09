package com.example.security.domain.mapping;

import com.example.security.domain.common.Severity;
import com.example.security.domain.event.LogEvent;
import com.example.security.domain.event.RawEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * OCSF (Open Cybersecurity Schema Framework) 매퍼.
 *
 * <p>OCSF 는 OASIS 가 추진하는 벤더 중립 보안 로그 스키마. 본 매퍼는 OCSF 의 핵심 필드들을
 * ECS 형태의 {@link LogEvent} 로 매핑한다 (본 시스템은 ECS 를 1차 도메인 모델로 사용).
 *
 * <p>OCSF 의 주요 필드와 ECS 매핑:
 *
 * <ul>
 *   <li>{@code class_uid} (예: 3002 = Authentication) → ECS {@code event.category}
 *   <li>{@code activity_id} (예: 1=Logon) → ECS {@code event.action}
 *   <li>{@code status_id} (1=Success, 2=Failure) → ECS {@code event.outcome}
 *   <li>{@code severity_id} (1~6) → ECS {@code event.severity} (0~100)
 *   <li>{@code time} (Unix epoch ms) → ECS {@code @timestamp}
 *   <li>{@code src_endpoint.ip} → ECS {@code source.ip}
 *   <li>{@code actor.user.name} → ECS {@code user.name}
 * </ul>
 *
 * <p>OCSF spec: <a href="https://schema.ocsf.io/">schema.ocsf.io</a>.
 */
public class OcsfNormalizer implements EventNormalizer {

  @Override
  public LogEvent normalize(RawEvent raw) {
    if (!"ocsf".equalsIgnoreCase(raw.schema())) {
      throw new UnsupportedSchemaException(raw.schema());
    }

    var p = raw.payload();
    var classUid = asInt(p.get("class_uid"), 0);
    var category = ocsfClassToEcsCategory(classUid);
    var action = ocsfActivityToEcsAction(classUid, asInt(p.get("activity_id"), 0));
    var outcome = ocsfStatusToEcsOutcome(asInt(p.get("status_id"), 0));
    var severity = ocsfSeverityToEcs(asInt(p.get("severity_id"), 1));

    var srcEndpoint = nestedMap(p, "src_endpoint");
    var dstEndpoint = nestedMap(p, "dst_endpoint");
    var actor = nestedMap(p, "actor");
    var actorUser = nestedMap(actor, "user");
    var device = nestedMap(p, "device");

    var labels = new HashMap<String, String>();
    labels.put("ocsf.class_uid", Integer.toString(classUid));
    labels.put("ocsf.activity_id", Integer.toString(asInt(p.get("activity_id"), 0)));

    return new LogEvent(
        eventIdOf(p),
        raw.tenantId(),
        parseOcsfTime(p.get("time"), raw.receivedAt()),
        raw.receivedAt(),
        "event",
        category,
        outcomeToEcsType(outcome),
        action,
        outcome,
        severity,
        asString(srcEndpoint.get("ip"), null),
        asInteger(srcEndpoint.get("port")),
        asString(dstEndpoint.get("ip"), null),
        asInteger(dstEndpoint.get("port")),
        asString(actorUser.get("name"), null),
        asString(device.get("hostname"), null),
        asString(nestedMap(device, "os").get("name"), null),
        asString(p.get("message"), ""),
        Map.copyOf(labels));
  }

  private static String ocsfClassToEcsCategory(int classUid) {
    // OCSF class_uid 의 일부 — 운영에서 빈도 높은 것만.
    return switch (classUid) {
      case 1001 -> "file"; // File System Activity
      case 1002 -> "process"; // Process Activity
      case 1004 -> "kernel"; // Kernel Extension Activity
      case 2001 -> "configuration";
      case 3002 -> "authentication";
      case 3003 -> "authorization";
      case 4001 -> "network";
      case 4002 -> "network"; // HTTP Activity
      case 4003 -> "dns";
      default -> "unknown";
    };
  }

  private static String ocsfActivityToEcsAction(int classUid, int activityId) {
    if (classUid == 3002) {
      return switch (activityId) {
        case 1 -> "logon";
        case 2 -> "logoff";
        case 3 -> "authentication_ticket";
        case 4 -> "service_authentication";
        default -> "authentication." + activityId;
      };
    }
    if (classUid == 4001 || classUid == 4002) {
      return switch (activityId) {
        case 1 -> "open";
        case 2 -> "close";
        case 6 -> "traffic";
        default -> "network." + activityId;
      };
    }
    return "activity." + activityId;
  }

  private static String ocsfStatusToEcsOutcome(int statusId) {
    return switch (statusId) {
      case 1 -> "success";
      case 2 -> "failure";
      case 3 -> "unknown"; // Other / Unknown
      default -> "unknown";
    };
  }

  private static Severity ocsfSeverityToEcs(int severityId) {
    // OCSF severity_id: 0=Unknown, 1=Informational, 2=Low, 3=Medium, 4=High, 5=Critical, 6=Fatal.
    return switch (severityId) {
      case 0, 1 -> Severity.INFO;
      case 2 -> Severity.LOW;
      case 3 -> Severity.MEDIUM;
      case 4 -> Severity.HIGH;
      case 5, 6 -> Severity.CRITICAL;
      default -> Severity.INFO;
    };
  }

  private static String outcomeToEcsType(String outcome) {
    return switch (outcome) {
      case "success" -> "allowed";
      case "failure" -> "denied";
      default -> "info";
    };
  }

  private static UUID eventIdOf(Map<String, Object> p) {
    var raw = p.get("event_uid");
    if (raw != null) {
      try {
        return UUID.fromString(raw.toString());
      } catch (IllegalArgumentException ignore) {
        return UUID.nameUUIDFromBytes(raw.toString().getBytes());
      }
    }
    return UUID.randomUUID();
  }

  private static Instant parseOcsfTime(Object v, Instant fallback) {
    if (v == null) return fallback;
    if (v instanceof Number n) {
      return Instant.ofEpochMilli(n.longValue());
    }
    try {
      return Instant.ofEpochMilli(Long.parseLong(v.toString()));
    } catch (NumberFormatException e) {
      try {
        return Instant.parse(v.toString());
      } catch (java.time.format.DateTimeParseException e2) {
        return fallback;
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> nestedMap(Map<String, Object> parent, String key) {
    var v = parent.get(key);
    if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
    return Map.of();
  }

  private static String asString(Object v, String fallback) {
    return v == null ? fallback : v.toString();
  }

  private static int asInt(Object v, int fallback) {
    if (v == null) return fallback;
    if (v instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(v.toString());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static Integer asInteger(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(v.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
