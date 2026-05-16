package com.example.security.application.sigma

import com.example.security.domain.sigma.SigmaRule
import java.io.StringReader
import java.time.Clock
import java.util.UUID
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Sigma YAML 파서 — SnakeYAML 의 SafeConstructor 만 허용 (RCE 방지).
 *
 * 1개 YAML 문서 또는 multi-document YAML (`---` 로 구분된 N 개) 양쪽 지원.
 */
open class SigmaYamlParser(private val clock: Clock) {

    open fun parseSingle(yaml: String): SigmaRule {
        val snake = newYaml()
        val obj = snake.load<Any?>(StringReader(yaml))
        require(obj is Map<*, *>) { "Sigma YAML 의 root 가 map 이 아님" }
        return toSigmaRule(obj, yaml)
    }

    /** multi-document YAML 또는 단일 YAML 모두 받아서 list 로 반환. */
    open fun parseAll(yaml: String): List<SigmaRule> {
        val snake = newYaml()
        val result = ArrayList<SigmaRule>()
        var idx = 0
        for (obj in snake.loadAll(StringReader(yaml))) {
            idx++
            if (obj == null) continue
            require(obj is Map<*, *>) { "Sigma YAML 문서 $idx 의 root 가 map 이 아님" }
            // multi-document 인 경우 source 를 통째로 보관 — 정확한 분리는 cost 대비 효익 낮음.
            result.add(toSigmaRule(obj, yaml))
        }
        return java.util.List.copyOf(result)
    }

    private fun newYaml(): Yaml {
        val loader = LoaderOptions()
        loader.maxAliasesForCollections = 50
        return Yaml(SafeConstructor(loader))
    }

    @Suppress("UNCHECKED_CAST")
    private fun toSigmaRule(root: Map<*, *>, source: String): SigmaRule {
        val rawId = root["id"]
        val id = rawId?.toString() ?: UUID.randomUUID().toString()
        val title = requireString(root["title"], "title")
        val description = stringOrNull(root["description"])
        val status = stringOrNull(root["status"])
        val level = stringOrNull(root["level"])
        val author = stringOrNull(root["author"])
        val refs = stringList(root["references"])
        val tags = stringList(root["tags"])
        val falsepositives = stringList(root["falsepositives"])
        val fields = stringList(root["fields"])

        val logsource = LinkedHashMap<String, String>()
        (root["logsource"] as? Map<*, *>)?.forEach { (k, v) ->
            if (k != null && v != null) logsource[k.toString()] = v.toString()
        }

        val detection: Map<String, Any> = (root["detection"] as? Map<*, *>)?.let {
            it as Map<String, Any>
        } ?: throw IllegalArgumentException("Sigma 룰 $title 에 detection 이 없음")

        return SigmaRule(
            id,
            title,
            description,
            status,
            level,
            author,
            refs,
            tags,
            falsepositives,
            java.util.Map.copyOf(logsource),
            detection,
            fields,
            source,
            clock.instant(),
        )
    }

    companion object {
        private fun requireString(v: Any?, key: String): String {
            requireNotNull(v) { "Sigma 룰 필수 필드 누락: $key" }
            return v.toString()
        }

        private fun stringOrNull(v: Any?): String? = v?.toString()

        private fun stringList(v: Any?): List<String> {
            if (v == null) return emptyList()
            if (v is List<*>) {
                val out = ArrayList<String>(v.size)
                for (item in v) {
                    if (item != null) out.add(item.toString())
                }
                return java.util.List.copyOf(out)
            }
            return listOf(v.toString())
        }
    }
}
