package com.example.security.domain.event;

import com.example.security.domain.tenant.Tenant.PiiMaskingPolicy;

/**
 * PII (개인정보 / 개인 식별 가능 정보) 마스킹 — ISMS-P 와 개인정보보호법 요구.
 *
 * <p>본 도메인 객체에 닿는 시점에 적용. application layer 의 SearchService 가 결과 export
 * 직전에 호출한다.
 */
public final class PiiMasker {

  private PiiMasker() {}

  public static LogEvent mask(LogEvent event, PiiMaskingPolicy policy) {
    return switch (policy) {
      case NONE -> event;
      case IP_ONLY -> maskIpOnly(event);
      case STRICT -> maskStrict(event);
    };
  }

  private static LogEvent maskIpOnly(LogEvent e) {
    return new LogEvent(
        e.eventId(),
        e.tenantId(),
        e.timestamp(),
        e.ingestedAt(),
        e.eventKind(),
        e.eventCategory(),
        e.eventType(),
        e.eventAction(),
        e.eventOutcome(),
        e.severity(),
        maskIp(e.sourceIp()),
        e.sourcePort(),
        maskIp(e.destinationIp()),
        e.destinationPort(),
        e.userName(),
        e.hostName(),
        e.hostOs(),
        e.message(),
        e.labels());
  }

  private static LogEvent maskStrict(LogEvent e) {
    return new LogEvent(
        e.eventId(),
        e.tenantId(),
        e.timestamp(),
        e.ingestedAt(),
        e.eventKind(),
        e.eventCategory(),
        e.eventType(),
        e.eventAction(),
        e.eventOutcome(),
        e.severity(),
        maskIp(e.sourceIp()),
        e.sourcePort(),
        maskIp(e.destinationIp()),
        e.destinationPort(),
        maskUser(e.userName()),
        e.hostName(),
        e.hostOs(),
        maskEmailInMessage(e.message()),
        e.labels());
  }

  /** IPv4 의 마지막 옥텟을 *** 로 치환. IPv6 는 마지막 그룹을. */
  static String maskIp(String ip) {
    if (ip == null) return null;
    if (ip.contains(".")) {
      var idx = ip.lastIndexOf('.');
      return ip.substring(0, idx) + ".***";
    }
    if (ip.contains(":")) {
      var idx = ip.lastIndexOf(':');
      return ip.substring(0, idx) + ":****";
    }
    return ip;
  }

  /** username 의 가운데를 마스킹 — alice → a***e, ab → ab. */
  static String maskUser(String name) {
    if (name == null || name.length() < 3) return name;
    return name.charAt(0) + "***" + name.charAt(name.length() - 1);
  }

  /** message 안의 이메일을 단순 패턴으로 마스킹. */
  static String maskEmailInMessage(String msg) {
    if (msg == null || msg.isEmpty()) return msg;
    return msg.replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "***@***");
  }
}
