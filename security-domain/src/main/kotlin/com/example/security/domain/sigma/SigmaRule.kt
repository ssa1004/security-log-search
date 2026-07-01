package com.example.security.domain.sigma

import java.io.Serializable
import java.time.Instant
import java.util.Objects

/**
 * Sigma 룰 — vendor 중립 SIEM 룰 표준.
 *
 * Sigma 는 SigmaHQ 가 정의한 YAML 기반 SIEM 탐지 룰 포맷이다 (OSS, MIT 라이선스).
 * 외부 위협 인텔리전스에서 배포되는 룰을 가져와 본 시스템의
 * [com.example.security.domain.rule.AlertRule] 로 변환할 수 있다.
 *
 * 본 클래스는 Sigma YAML 의 핵심 필드를 담는 표현이다. 변환되지 않은 detection 본문은
 * `detection` 필드에 raw map 으로 보관한다 ([SigmaToAlertRuleMapper] 가 사용).
 *
 * 컬렉션 필드는 생성 시 불변 복사본으로 보관한다 — Java record 호환을 위해 일반 class +
 * custom equals/hashCode 로 두고 accessor 이름은 `id()` / `detection()` 형태를 유지한다.
 *
 * Sigma spec: https://github.com/SigmaHQ/sigma-specification
 */
class SigmaRule(
    /** Sigma `id` — UUID. */
    id: String,
    /** Sigma `title`. */
    title: String,
    /** Sigma `description`. */
    description: String?,
    /** Sigma `status` — stable / test / experimental / deprecated / unsupported. */
    status: String?,
    /** Sigma `level` — informational / low / medium / high / critical. */
    level: String?,
    /** Sigma `author`. */
    author: String?,
    /** Sigma `references` — URL 목록. */
    references: List<String>?,
    /** Sigma `tags` — MITRE ATT&CK 등 (예: attack.t1110). */
    tags: List<String>?,
    /** Sigma `falsepositives` — false positive 시나리오. */
    falsepositives: List<String>?,
    /** Sigma `logsource` — category / product / service. */
    logsource: Map<String, String>?,
    /** Sigma `detection` — selection 들 + condition. raw map. */
    detection: Map<String, Any>,
    /** Sigma `fields` — 운영자가 검색 결과에서 보고 싶어하는 필드. */
    fields: List<String>?,
    /** Sigma YAML 원본 (감사 / 재변환용 보관). */
    source: String,
    /** import 한 시각. */
    importedAt: Instant,
) : Serializable {

    @get:JvmName("id")
    val id: String = id

    @get:JvmName("title")
    val title: String = title

    @get:JvmName("description")
    val description: String? = description

    @get:JvmName("status")
    val status: String? = status

    @get:JvmName("level")
    val level: String? = level

    @get:JvmName("author")
    val author: String? = author

    @get:JvmName("references")
    val references: List<String> =
        if (references == null) emptyList() else java.util.List.copyOf(references)

    @get:JvmName("tags")
    val tags: List<String> = if (tags == null) emptyList() else java.util.List.copyOf(tags)

    @get:JvmName("falsepositives")
    val falsepositives: List<String> =
        if (falsepositives == null) emptyList() else java.util.List.copyOf(falsepositives)

    @get:JvmName("logsource")
    val logsource: Map<String, String> =
        if (logsource == null) emptyMap() else java.util.Map.copyOf(logsource)

    @get:JvmName("detection")
    val detection: Map<String, Any> = run {
        // JSON/Kafka 값에 null 이 섞이면 Map.copyOf 가 NPE 를 던진다. null 값 엔트리는 '없음'으로 보고 떨궈 불변 복사.
        val src: Map<String, Any?> = detection
        val copy = LinkedHashMap<String, Any>()
        for ((k, v) in src) if (v != null) copy[k] = v
        java.util.Collections.unmodifiableMap(copy)
    }

    @get:JvmName("fields")
    val fields: List<String> = if (fields == null) emptyList() else java.util.List.copyOf(fields)

    @get:JvmName("source")
    val source: String = source

    @get:JvmName("importedAt")
    val importedAt: Instant = importedAt

    /** detection.condition 문자열 추출 — 없으면 null. */
    fun condition(): String? = detection["condition"]?.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SigmaRule) return false
        return id == other.id &&
            title == other.title &&
            description == other.description &&
            status == other.status &&
            level == other.level &&
            author == other.author &&
            references == other.references &&
            tags == other.tags &&
            falsepositives == other.falsepositives &&
            logsource == other.logsource &&
            detection == other.detection &&
            fields == other.fields &&
            source == other.source &&
            importedAt == other.importedAt
    }

    override fun hashCode(): Int =
        Objects.hash(
            id,
            title,
            description,
            status,
            level,
            author,
            references,
            tags,
            falsepositives,
            logsource,
            detection,
            fields,
            source,
            importedAt,
        )

    override fun toString(): String =
        "SigmaRule[id=$id, title=$title, description=$description, status=$status, " +
            "level=$level, author=$author, references=$references, tags=$tags, " +
            "falsepositives=$falsepositives, logsource=$logsource, detection=$detection, " +
            "fields=$fields, source=$source, importedAt=$importedAt]"

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
