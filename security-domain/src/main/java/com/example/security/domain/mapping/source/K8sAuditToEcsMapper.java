package com.example.security.domain.mapping.source;

import com.example.security.domain.common.Severity;
import com.example.security.domain.event.LogEvent;
import com.example.security.domain.event.RawEvent;
import com.example.security.domain.mapping.EventNormalizer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kubernetes audit log JSON → ECS {@link LogEvent} 변환기.
 *
 * <p>kube-apiserver 의 audit policy 로 생성되는 JSON 레코드 (kind=Event, apiVersion=audit.k8s.io/v1)
 * 를 SIEM 의 정규화 모델로 변환한다.
 *
 * <p>매핑 규칙 (K8s audit 필드 → ECS):
 *
 * <ul>
 *   <li>{@code requestReceivedTimestamp} → {@code @timestamp} (없으면 {@code stageTimestamp})
 *   <li>{@code verb} → {@code event.action} (get / list / create / update / delete / watch)
 *   <li>{@code user.username} → {@code user.name}
 *   <li>{@code user.groups} → labels {@code user.groups}
 *   <li>{@code objectRef.namespace} → labels {@code kubernetes.namespace}
 *   <li>{@code objectRef.resource} / {@code subresource} / {@code name} → labels {@code kubernetes.*}
 *   <li>{@code responseStatus.code} → {@code event.outcome} (2xx/3xx success, 4xx/5xx failure)
 *   <li>{@code sourceIPs[0]} → {@code source.ip}
 *   <li>{@code stage} → labels {@code kubernetes.audit.stage}
 *   <li>{@code requestURI} → message 의 보강 정보
 * </ul>
 *
 * <p>ECS event.category 는 verb / requestURI 로 결정:
 *
 * <ul>
 *   <li>{@code create / update / patch / delete} → {@code configuration}
 *   <li>{@code authentication} 관련 endpoint → {@code authentication}
 *   <li>그 외 (get / list / watch) → {@code api}
 * </ul>
 *
 * <p>참고:
 *
 * <ul>
 *   <li>K8s audit log spec: <a
 *       href="https://kubernetes.io/docs/tasks/debug/debug-cluster/audit/">kubernetes.io/docs/tasks/debug/debug-cluster/audit</a>
 *   <li>ECS spec: <a href="https://www.elastic.co/guide/en/ecs/current/index.html">elastic.co/ecs</a>
 * </ul>
 */
public class K8sAuditToEcsMapper implements EventNormalizer {

  /** RawEvent.schema 가 이 값일 때만 본 매퍼가 처리한다. */
  public static final String SCHEMA = "k8s-audit";

  @Override
  public LogEvent normalize(RawEvent raw) {
    if (!SCHEMA.equalsIgnoreCase(raw.schema())) {
      throw new UnsupportedSchemaException(raw.schema());
    }

    var p = raw.payload();
    var verb = asString(p.get("verb"), null);
    var user = nestedMap(p, "user");
    var objectRef = nestedMap(p, "objectRef");
    var responseStatus = nestedMap(p, "responseStatus");
    var responseCode = asInt(responseStatus.get("code"), 0);
    var outcome = outcomeOf(responseCode);
    var category = categoryOf(verb, asString(p.get("requestURI"), null));

    var labels = new LinkedHashMap<String, String>();
    labels.put("kubernetes.audit.stage", asString(p.get("stage"), "ResponseComplete"));
    putIfPresent(labels, "kubernetes.audit.level", asString(p.get("level"), null));
    putIfPresent(labels, "kubernetes.audit.request_uri", asString(p.get("requestURI"), null));
    putIfPresent(labels, "kubernetes.namespace", asString(objectRef.get("namespace"), null));
    putIfPresent(labels, "kubernetes.api_group", asString(objectRef.get("apiGroup"), null));
    putIfPresent(labels, "kubernetes.api_version", asString(objectRef.get("apiVersion"), null));
    putIfPresent(labels, "kubernetes.resource", asString(objectRef.get("resource"), null));
    putIfPresent(labels, "kubernetes.subresource", asString(objectRef.get("subresource"), null));
    putIfPresent(labels, "kubernetes.object_name", asString(objectRef.get("name"), null));
    putIfPresent(labels, "user.id", asString(user.get("uid"), null));
    var groups = user.get("groups");
    if (groups instanceof List<?> g && !g.isEmpty()) {
      labels.put("user.groups", String.join(",", g.stream().map(Object::toString).toList()));
    }
    if (responseCode > 0) {
      labels.put("http.response.status_code", Integer.toString(responseCode));
    }

    var sourceIp = firstSourceIp(p.get("sourceIPs"));
    var hostName = asString(p.get("kind"), "Event");
    var severity = severityOf(outcome, verb, responseCode);

    return new LogEvent(
        eventIdOf(p),
        raw.tenantId(),
        parseTimestamp(p.get("requestReceivedTimestamp"), p.get("stageTimestamp"), raw.receivedAt()),
        raw.receivedAt(),
        "event",
        category,
        outcomeToType(outcome),
        verb,
        outcome,
        severity,
        sourceIp,
        null,
        null,
        null,
        asString(user.get("username"), null),
        hostName,
        null,
        messageOf(verb, asString(user.get("username"), null), labels.get("kubernetes.resource"), labels.get("kubernetes.namespace"), responseCode),
        Map.copyOf(labels));
  }

  private static String categoryOf(String verb, String requestUri) {
    if (requestUri != null) {
      var u = requestUri.toLowerCase(java.util.Locale.ROOT);
      if (u.contains("/authentication.k8s.io/") || u.contains("tokenreviews") || u.contains("subjectaccessreviews")) {
        return "authentication";
      }
    }
    if (verb == null) return "unknown";
    return switch (verb.toLowerCase(java.util.Locale.ROOT)) {
      case "create", "update", "patch", "delete", "deletecollection" -> "configuration";
      case "get", "list", "watch" -> "api";
      default -> "api";
    };
  }

  private static String outcomeOf(int code) {
    if (code == 0) return "unknown";
    if (code >= 200 && code < 400) return "success";
    return "failure";
  }

  private static String outcomeToType(String outcome) {
    return switch (outcome) {
      case "success" -> "allowed";
      case "failure" -> "denied";
      default -> "info";
    };
  }

  private static Severity severityOf(String outcome, String verb, int code) {
    if ("failure".equals(outcome)) {
      if (code == 401 || code == 403) return Severity.HIGH; // 인증 / 인가 실패
      if (code >= 500) return Severity.MEDIUM;
      return Severity.LOW;
    }
    if (verb != null) {
      var v = verb.toLowerCase(java.util.Locale.ROOT);
      if ("delete".equals(v) || "deletecollection".equals(v)) return Severity.MEDIUM;
      if ("create".equals(v) || "update".equals(v) || "patch".equals(v)) return Severity.LOW;
    }
    return Severity.INFO;
  }

  private static String messageOf(String verb, String user, String resource, String namespace, int code) {
    var sb = new StringBuilder();
    sb.append("k8s-audit ");
    sb.append(verb == null ? "?" : verb);
    if (resource != null) {
      sb.append(' ').append(resource);
    }
    if (namespace != null) {
      sb.append(" in ").append(namespace);
    }
    sb.append(" by ").append(user == null ? "(unknown)" : user);
    if (code > 0) {
      sb.append(" → ").append(code);
    }
    return sb.toString();
  }

  private static UUID eventIdOf(Map<String, Object> p) {
    var id = p.get("auditID");
    if (id != null) {
      try {
        return UUID.fromString(id.toString());
      } catch (IllegalArgumentException ignore) {
        return UUID.nameUUIDFromBytes(id.toString().getBytes());
      }
    }
    return UUID.randomUUID();
  }

  private static Instant parseTimestamp(Object primary, Object secondary, Instant fallback) {
    var picked = primary != null ? primary : secondary;
    if (picked == null) return fallback;
    try {
      return Instant.parse(picked.toString());
    } catch (java.time.format.DateTimeParseException e) {
      return fallback;
    }
  }

  private static String firstSourceIp(Object value) {
    if (value instanceof List<?> list && !list.isEmpty()) {
      var first = list.get(0);
      return first == null ? null : first.toString();
    }
    return null;
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

  private static void putIfPresent(Map<String, String> labels, String key, String value) {
    if (value != null && !value.isBlank()) {
      labels.put(key, value);
    }
  }
}
