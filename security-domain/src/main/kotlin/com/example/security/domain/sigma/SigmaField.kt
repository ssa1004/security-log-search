package com.example.security.domain.sigma

import java.util.Locale

/**
 * Sigma detection field 표기 — `FieldName|modifier1|modifier2`.
 *
 * Sigma spec 의 modifier 일부:
 * - `contains`, `startswith`, `endswith` — 부분 매칭
 * - `equals` — 정확 매칭 (modifier 생략 시 default 도 equals)
 * - `re` — 정규표현식 (본 매퍼는 미지원)
 * - `base64` / `base64offset` / `utf16` 등 — 인코딩 (미지원)
 * - `all` — 모든 값이 매칭되어야 함 (미지원)
 */
@JvmRecord
data class SigmaField(val name: String, val modifiers: List<String>) {

    fun isEqualsLike(): Boolean {
        if (modifiers.isEmpty()) return true // default equals
        // 모든 modifier 가 equals-like 일 때만 true.
        for (m in modifiers) {
            if (m !in EQUALS_LIKE) return false
        }
        return true
    }

    companion object {
        /** equals-like 인 modifier set — 본 매퍼가 supported 로 간주. */
        private val EQUALS_LIKE: Set<String> =
            setOf("equals", "contains", "startswith", "endswith")

        @JvmStatic
        fun parse(raw: String?): SigmaField {
            require(!raw.isNullOrBlank()) { "field 이름이 비어 있음" }
            // Java 의 String.split("\\|") (limit 0) 와 동일하게 trailing empty 제거.
            val parts = raw.split(Regex("\\|")).dropLastWhile { it.isEmpty() }
            val name = parts[0].trim()
            if (parts.size == 1) {
                return SigmaField(name, emptyList())
            }
            val modifiers = ArrayList<String>()
            for (i in 1 until parts.size) {
                modifiers.add(parts[i].trim().lowercase(Locale.ROOT))
            }
            return SigmaField(name, java.util.List.copyOf(modifiers))
        }
    }
}
