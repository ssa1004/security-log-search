package com.example.security.domain.sigma

import java.util.Locale

/**
 * Sigma 의 vendor-native 필드 이름을 ECS (Elastic Common Schema) 필드로 변환.
 *
 * Sigma 룰은 product 별로 native 필드 이름을 사용한다 (예: Windows `EventID`, Linux
 * `auditd.SYSCALL`). 본 맵은 SIEM 에서 가장 자주 등장하는 필드 일부만 정규화한다.
 *
 * 매핑 규칙은 ECS 의 logs.* / events.* spec 과 SigmaHQ 의 `pySigma-backend-elasticsearch`
 * pipeline 을 참고했다. 모든 필드를 다 매핑하지는 않으며, 매핑 안 되는 키는 원본 이름을 유지한다.
 */
object SigmaFieldNameMap {

    /** 대소문자 구분 없는 직접 매핑 — sigma → ECS. */
    private val DIRECT: Map<String, String> =
        mapOf(
            // Windows event log
            "eventid" to "event.code",
            "event_id" to "event.code",
            "computername" to "host.hostname",
            "targetusername" to "user.name",
            "subjectusername" to "user.name",
            "sourceuser" to "user.name",
            "ipaddress" to "source.ip",
            "sourceip" to "source.ip",
            "destinationip" to "destination.ip",
            "sourceport" to "source.port",
            "destinationport" to "destination.port",
            // logon outcome — Windows EventID 4624 = success, 4625 = failure 식이지만 sigma 에서
            // 직접 status 키를 쓰는 경우 매핑.
            "status" to "event.outcome",
            "logonsuccess" to "event.outcome",
            // process / file
            "image" to "process.executable",
            "commandline" to "process.command_line",
            "parentimage" to "process.parent.executable",
            "targetfilename" to "file.path",
            "filename" to "file.name",
            // network
            "destinationhostname" to "destination.domain",
            "uri" to "url.original",
            "c-uri" to "url.original",
            "useragent" to "user_agent.original",
            // 우리 룰 DSL 에서 직접 쓰는 필드는 그대로 통과하도록 keys 의 lower 매핑 추가.
            "source.ip" to "source.ip",
            "user.name" to "user.name",
            "host.hostname" to "host.hostname",
            "event.action" to "event.action",
            "event.outcome" to "event.outcome",
            "event.category" to "event.category",
        )

    @JvmStatic
    fun toEcs(sigmaField: String?): String? {
        if (sigmaField == null) return null
        val key = sigmaField.lowercase(Locale.ROOT)
        return DIRECT[key] ?: sigmaField
    }
}
