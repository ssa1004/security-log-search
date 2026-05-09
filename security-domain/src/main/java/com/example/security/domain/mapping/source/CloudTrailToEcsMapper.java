package com.example.security.domain.mapping.source;

import com.example.security.domain.common.Severity;
import com.example.security.domain.event.LogEvent;
import com.example.security.domain.event.RawEvent;
import com.example.security.domain.mapping.EventNormalizer;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AWS CloudTrail 레코드 → ECS {@link LogEvent} 변환기.
 *
 * <p>CloudTrail 은 AWS 계정 활동 (API call / management event / data event) 을 JSON 으로
 * 기록한다. SIEM 에서는 이 raw 레코드를 그대로 받아 ECS 로 정규화해야 검색 / 룰 평가가 가능하다.
 *
 * <p>매핑 규칙 (CloudTrail 필드 → ECS):
 *
 * <ul>
 *   <li>{@code eventTime} → {@code @timestamp}
 *   <li>{@code eventName} → {@code event.action} (예: ConsoleLogin / AssumeRole)
 *   <li>{@code eventSource} → {@code event.provider} (예: signin.amazonaws.com)
 *   <li>{@code userIdentity.arn} → {@code user.id}
 *   <li>{@code userIdentity.userName} → {@code user.name} (없으면 {@code sessionContext} 의
 *       principalId 로 fallback)
 *   <li>{@code sourceIPAddress} → {@code source.ip}
 *   <li>{@code awsRegion} → {@code cloud.region} (labels)
 *   <li>{@code requestParameters} → {@code event.original} (labels 에 JSON 문자열 보존)
 *   <li>{@code errorCode} 존재 → {@code event.outcome=failure}, 없으면 {@code success}
 * </ul>
 *
 * <p>ECS event.category 는 CloudTrail 대부분이 IAM / API 콜 → {@code authentication} (ConsoleLogin,
 * AssumeRole 등) 또는 {@code iam} / {@code configuration} 으로 분기한다. 본 매퍼는 단순화를
 *위해 이벤트 이름의 prefix 로 분류한다 (정확한 카테고리는 후속 enrichment 단계에서 보강).
 *
 * <p>참고:
 *
 * <ul>
 *   <li>CloudTrail record reference: AWS docs — User Guide / cloudtrail-event-reference-record-contents
 *   <li>ECS spec: <a href="https://www.elastic.co/guide/en/ecs/current/index.html">elastic.co/ecs</a>
 * </ul>
 */
public class CloudTrailToEcsMapper implements EventNormalizer {

  /** RawEvent.schema 가 이 값일 때만 본 매퍼가 처리한다. */
  public static final String SCHEMA = "aws-cloudtrail";

  @Override
  public LogEvent normalize(RawEvent raw) {
    if (!SCHEMA.equalsIgnoreCase(raw.schema())) {
      throw new UnsupportedSchemaException(raw.schema());
    }

    var p = raw.payload();
    var eventName = asString(p.get("eventName"), null);
    var eventSource = asString(p.get("eventSource"), null);
    var errorCode = asString(p.get("errorCode"), null);
    var outcome = errorCode == null ? "success" : "failure";
    var category = categoryOf(eventName, eventSource);

    var userIdentity = nestedMap(p, "userIdentity");
    var sessionContext = nestedMap(userIdentity, "sessionContext");
    var sessionIssuer = nestedMap(sessionContext, "sessionIssuer");
    var userArn = asString(userIdentity.get("arn"), null);
    var userName = userNameOf(userIdentity, sessionIssuer);

    var labels = new LinkedHashMap<String, String>();
    putIfPresent(labels, "event.provider", eventSource);
    putIfPresent(labels, "user.id", userArn);
    putIfPresent(labels, "cloud.provider", "aws");
    putIfPresent(labels, "cloud.region", asString(p.get("awsRegion"), null));
    putIfPresent(labels, "cloud.account.id", asString(p.get("recipientAccountId"), null));
    putIfPresent(labels, "aws.cloudtrail.event_type", asString(p.get("eventType"), null));
    putIfPresent(labels, "aws.cloudtrail.user_identity.type", asString(userIdentity.get("type"), null));
    putIfPresent(labels, "aws.cloudtrail.user_identity.principal_id", asString(userIdentity.get("principalId"), null));
    if (errorCode != null) {
      putIfPresent(labels, "error.code", errorCode);
      putIfPresent(labels, "error.message", asString(p.get("errorMessage"), null));
    }
    var requestParams = p.get("requestParameters");
    if (requestParams != null) {
      labels.put("event.original.request_parameters", requestParams.toString());
    }
    var responseElements = p.get("responseElements");
    if (responseElements != null) {
      labels.put("event.original.response_elements", responseElements.toString());
    }

    var severity = severityOf(outcome, category);
    var sourceIp = asString(p.get("sourceIPAddress"), null);
    var hostName = asString(p.get("recipientAccountId"), null);

    return new LogEvent(
        eventIdOf(p),
        raw.tenantId(),
        parseEventTime(p.get("eventTime"), raw.receivedAt()),
        raw.receivedAt(),
        "event",
        category,
        outcome.equals("success") ? "allowed" : "denied",
        eventName == null ? null : eventName,
        outcome,
        severity,
        sourceIp,
        null,
        null,
        null,
        userName,
        hostName,
        null,
        messageOf(eventName, userName, outcome),
        Map.copyOf(labels));
  }

  private static String categoryOf(String eventName, String eventSource) {
    if (eventName == null) return "unknown";
    var n = eventName.toLowerCase(java.util.Locale.ROOT);
    if (n.startsWith("consolelogin") || n.contains("login") || n.contains("assumerole") || n.contains("getsessiontoken")) {
      return "authentication";
    }
    if (n.startsWith("create") || n.startsWith("update") || n.startsWith("put") || n.startsWith("delete") || n.startsWith("modify")) {
      return "configuration";
    }
    if (eventSource != null && eventSource.contains("iam.amazonaws.com")) {
      return "iam";
    }
    return "api";
  }

  private static String userNameOf(Map<String, Object> userIdentity, Map<String, Object> sessionIssuer) {
    var direct = asString(userIdentity.get("userName"), null);
    if (direct != null) return direct;
    var issuerName = asString(sessionIssuer.get("userName"), null);
    if (issuerName != null) return issuerName;
    return asString(userIdentity.get("principalId"), null);
  }

  private static Severity severityOf(String outcome, String category) {
    if ("failure".equals(outcome) && "authentication".equals(category)) {
      return Severity.HIGH;
    }
    if ("failure".equals(outcome)) {
      return Severity.MEDIUM;
    }
    if ("authentication".equals(category)) {
      return Severity.LOW;
    }
    return Severity.INFO;
  }

  private static String messageOf(String eventName, String userName, String outcome) {
    if (eventName == null) return "";
    var who = userName == null ? "(unknown)" : userName;
    return "CloudTrail " + eventName + " by " + who + " — " + outcome;
  }

  private static UUID eventIdOf(Map<String, Object> p) {
    var id = p.get("eventID");
    if (id != null) {
      try {
        return UUID.fromString(id.toString());
      } catch (IllegalArgumentException ignore) {
        return UUID.nameUUIDFromBytes(id.toString().getBytes());
      }
    }
    return UUID.randomUUID();
  }

  private static Instant parseEventTime(Object value, Instant fallback) {
    if (value == null) return fallback;
    try {
      return Instant.parse(value.toString());
    } catch (java.time.format.DateTimeParseException e) {
      return fallback;
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

  private static void putIfPresent(Map<String, String> labels, String key, String value) {
    if (value != null && !value.isBlank()) {
      labels.put(key, value);
    }
  }

  /** 명시적으로 mutable map 이 필요할 때 사용 — 본 클래스는 주로 LinkedHashMap. */
  @SuppressWarnings("unused")
  private static HashMap<String, String> mutable(Map<String, String> base) {
    return new HashMap<>(base);
  }
}
