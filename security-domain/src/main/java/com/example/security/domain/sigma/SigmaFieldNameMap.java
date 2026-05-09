package com.example.security.domain.sigma;

import java.util.Locale;
import java.util.Map;

/**
 * Sigma 의 vendor-native 필드 이름을 ECS (Elastic Common Schema) 필드로 변환.
 *
 * <p>Sigma 룰은 product 별로 native 필드 이름을 사용한다 (예: Windows {@code EventID}, Linux
 * {@code auditd.SYSCALL}). 본 맵은 SIEM 에서 가장 자주 등장하는 필드 일부만 정규화한다.
 *
 * <p>매핑 규칙은 ECS 의 logs.* / events.* spec 과 SigmaHQ 의 {@code pySigma-backend-elasticsearch}
 * pipeline 을 참고했다. 모든 필드를 다 매핑하지는 않으며, 매핑 안 되는 키는 원본 이름을 유지한다.
 */
public final class SigmaFieldNameMap {

  private SigmaFieldNameMap() {}

  /** 대소문자 구분 없는 직접 매핑 — sigma → ECS. */
  private static final Map<String, String> DIRECT =
      Map.ofEntries(
          // Windows event log
          Map.entry("eventid", "event.code"),
          Map.entry("event_id", "event.code"),
          Map.entry("computername", "host.hostname"),
          Map.entry("targetusername", "user.name"),
          Map.entry("subjectusername", "user.name"),
          Map.entry("sourceuser", "user.name"),
          Map.entry("ipaddress", "source.ip"),
          Map.entry("sourceip", "source.ip"),
          Map.entry("destinationip", "destination.ip"),
          Map.entry("sourceport", "source.port"),
          Map.entry("destinationport", "destination.port"),
          // logon outcome — Windows EventID 4624 = success, 4625 = failure 식이지만 sigma 에서
          // 직접 status 키를 쓰는 경우 매핑.
          Map.entry("status", "event.outcome"),
          Map.entry("logonsuccess", "event.outcome"),
          // process / file
          Map.entry("image", "process.executable"),
          Map.entry("commandline", "process.command_line"),
          Map.entry("parentimage", "process.parent.executable"),
          Map.entry("targetfilename", "file.path"),
          Map.entry("filename", "file.name"),
          // network
          Map.entry("destinationhostname", "destination.domain"),
          Map.entry("uri", "url.original"),
          Map.entry("c-uri", "url.original"),
          Map.entry("useragent", "user_agent.original"),
          // 우리 룰 DSL 에서 직접 쓰는 필드는 그대로 통과하도록 keys 의 lower 매핑 추가.
          Map.entry("source.ip", "source.ip"),
          Map.entry("user.name", "user.name"),
          Map.entry("host.hostname", "host.hostname"),
          Map.entry("event.action", "event.action"),
          Map.entry("event.outcome", "event.outcome"),
          Map.entry("event.category", "event.category"));

  public static String toEcs(String sigmaField) {
    if (sigmaField == null) return null;
    var key = sigmaField.toLowerCase(Locale.ROOT);
    var mapped = DIRECT.get(key);
    return mapped == null ? sigmaField : mapped;
  }
}
