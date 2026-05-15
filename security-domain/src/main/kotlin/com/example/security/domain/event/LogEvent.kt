package com.example.security.domain.event

import com.example.security.domain.common.Severity
import com.example.security.domain.common.TenantId
import java.io.Serializable
import java.time.Instant
import java.time.ZoneOffset
import java.util.Objects
import java.util.UUID

/**
 * 정규화된 보안 로그 이벤트 — ECS (Elastic Common Schema) 8.x 를 1차 모델로 한 도메인 객체.
 *
 * ECS 의 핵심 필드들을 평탄화했다. raw event 는 다양한 source (방화벽 / EDR / 시스템 / 응용)
 * 에서 오지만 본 객체로 정규화된 후에는 일관된 검색 / 집계가 가능하다.
 *
 * OCSF (Open Cybersecurity Schema Framework) 매핑은 별도 mapper 가 담당한다 — 본 도메인은
 * ECS 필드 이름 (snake_case) 을 따른다.
 *
 * 세부 필드 그룹 (ECS spec 기준):
 * - `event.*` — kind, category, type, action, outcome, severity
 * - `source.*` / `destination.*` — IP, port, address
 * - `user.*` — name, id, domain
 * - `host.*` — hostname, os
 * - `labels` — 자유 형식 key-value (raw 에서 정규화 안 된 항목)
 *
 * `labels` 는 생성 시 불변 복사본으로 보관한다 — 호출 측이 이후 원본 맵을 바꿔도 영향 없음.
 * Flink 스트림 타입이자 Java record 호환을 위해 일반 class + custom equals/hashCode 로 두고
 * accessor 이름은 `eventId()` / `labels()` 형태를 유지한다.
 */
class LogEvent(
    /** 이벤트 식별자. idempotency 키이자 OpenSearch _id, ClickHouse event_id 로 사용. */
    eventId: UUID,
    /** 테넌트. */
    tenantId: TenantId,
    /** 이벤트 발생 시각 (source 가 보고한 시각). */
    timestamp: Instant,
    /** 시스템에 수집된 시각 — `@timestamp` 와 별도로 audit 에 사용. */
    ingestedAt: Instant,
    /** ECS event.kind — event / alert / metric / state / signal */
    eventKind: String?,
    /** ECS event.category — authentication / network / process / file / web 등 */
    eventCategory: String?,
    /** ECS event.type — start / end / info / change / denied / allowed 등 */
    eventType: String?,
    /** ECS event.action — login / logout / connection_dropped 등 도메인 동사. */
    eventAction: String?,
    /** ECS event.outcome — success / failure / unknown */
    eventOutcome: String?,
    /** 5단계 심각도 (ECS event.severity 매핑). */
    severity: Severity,
    /** ECS source.ip. */
    sourceIp: String?,
    /** ECS source.port. */
    sourcePort: Int?,
    /** ECS destination.ip. */
    destinationIp: String?,
    /** ECS destination.port. */
    destinationPort: Int?,
    /** ECS user.name (또는 id, login). */
    userName: String?,
    /** ECS host.hostname. */
    hostName: String?,
    /** ECS host.os.name (linux / windows / macos / ios / android 등). */
    hostOs: String?,
    /** ECS message — 사람이 읽는 한 줄 요약. */
    message: String?,
    /** 자유형 라벨 — 정규화 안 된 raw 필드 보관. */
    labels: Map<String, String>,
) : Serializable {

    @get:JvmName("eventId")
    val eventId: UUID = eventId

    @get:JvmName("tenantId")
    val tenantId: TenantId = tenantId

    @get:JvmName("timestamp")
    val timestamp: Instant = timestamp

    @get:JvmName("ingestedAt")
    val ingestedAt: Instant = ingestedAt

    @get:JvmName("eventKind")
    val eventKind: String? = eventKind

    @get:JvmName("eventCategory")
    val eventCategory: String? = eventCategory

    @get:JvmName("eventType")
    val eventType: String? = eventType

    @get:JvmName("eventAction")
    val eventAction: String? = eventAction

    @get:JvmName("eventOutcome")
    val eventOutcome: String? = eventOutcome

    @get:JvmName("severity")
    val severity: Severity = severity

    @get:JvmName("sourceIp")
    val sourceIp: String? = sourceIp

    @get:JvmName("sourcePort")
    val sourcePort: Int? = sourcePort

    @get:JvmName("destinationIp")
    val destinationIp: String? = destinationIp

    @get:JvmName("destinationPort")
    val destinationPort: Int? = destinationPort

    @get:JvmName("userName")
    val userName: String? = userName

    @get:JvmName("hostName")
    val hostName: String? = hostName

    @get:JvmName("hostOs")
    val hostOs: String? = hostOs

    @get:JvmName("message")
    val message: String? = message

    @get:JvmName("labels")
    val labels: Map<String, String> = java.util.Map.copyOf(labels)

    init {
        if (timestamp.isAfter(ingestedAt.plusSeconds(60))) {
            // source 가 미래 시각을 보낸 경우 — clock skew 허용 60초.
            throw IllegalArgumentException(
                "이벤트 timestamp 가 ingestedAt + 60s 보다 미래: $timestamp vs $ingestedAt",
            )
        }
    }

    /** OpenSearch index 이름. `events-{tenant}-{yyyy.MM.dd}` 패턴. */
    fun openSearchIndexName(): String {
        val date = timestamp.atZone(ZoneOffset.UTC).toLocalDate()
        return "events-%s-%04d.%02d.%02d".format(
            tenantId.value,
            date.year,
            date.monthValue,
            date.dayOfMonth,
        )
    }

    /** OpenSearch write alias — 인덱스 직접 쓰지 않고 alias 로 추상화. */
    fun openSearchWriteAlias(): String = "events-${tenantId.value}-write"

    /** 인증 실패 이벤트 여부 — Flink correlation rule 의 default 한 가지. */
    fun isAuthFailure(): Boolean =
        "authentication" == eventCategory && "failure" == eventOutcome

    /** 인증 성공 이벤트 여부. */
    fun isAuthSuccess(): Boolean =
        "authentication" == eventCategory && "success" == eventOutcome

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LogEvent) return false
        return eventId == other.eventId &&
            tenantId == other.tenantId &&
            timestamp == other.timestamp &&
            ingestedAt == other.ingestedAt &&
            eventKind == other.eventKind &&
            eventCategory == other.eventCategory &&
            eventType == other.eventType &&
            eventAction == other.eventAction &&
            eventOutcome == other.eventOutcome &&
            severity == other.severity &&
            sourceIp == other.sourceIp &&
            sourcePort == other.sourcePort &&
            destinationIp == other.destinationIp &&
            destinationPort == other.destinationPort &&
            userName == other.userName &&
            hostName == other.hostName &&
            hostOs == other.hostOs &&
            message == other.message &&
            labels == other.labels
    }

    override fun hashCode(): Int =
        Objects.hash(
            eventId,
            tenantId,
            timestamp,
            ingestedAt,
            eventKind,
            eventCategory,
            eventType,
            eventAction,
            eventOutcome,
            severity,
            sourceIp,
            sourcePort,
            destinationIp,
            destinationPort,
            userName,
            hostName,
            hostOs,
            message,
            labels,
        )

    override fun toString(): String =
        "LogEvent[eventId=$eventId, tenantId=$tenantId, timestamp=$timestamp, " +
            "ingestedAt=$ingestedAt, eventKind=$eventKind, eventCategory=$eventCategory, " +
            "eventType=$eventType, eventAction=$eventAction, eventOutcome=$eventOutcome, " +
            "severity=$severity, sourceIp=$sourceIp, sourcePort=$sourcePort, " +
            "destinationIp=$destinationIp, destinationPort=$destinationPort, " +
            "userName=$userName, hostName=$hostName, hostOs=$hostOs, message=$message, " +
            "labels=$labels]"

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
