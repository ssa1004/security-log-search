package com.example.security.domain.common

import java.io.Serializable
import java.util.regex.Pattern

/**
 * 테넌트 식별자 값 객체. 멀티테넌트 격리의 핵심 키.
 *
 * OpenSearch 인덱스 이름 (events-{tenantId}-...), ClickHouse Row Policy 의 currentSetting,
 * JWT claim, 모든 audit row 의 키로 사용되므로 형식을 엄격히 검증한다.
 *
 * 제약:
 * - 소문자 영숫자 + 하이픈만 허용 — OpenSearch 인덱스 이름 규칙과 동일
 * - 2~32자 — 너무 짧으면 충돌, 너무 길면 인덱스 이름이 ES 한계 (255자) 에 가까워짐
 * - 하이픈으로 시작 / 끝 금지
 */
@JvmRecord
data class TenantId(val value: String) : Serializable {

    init {
        require(VALID.matcher(value).matches()) {
            "tenantId 는 소문자 영숫자 + 하이픈, 2~32자, 하이픈으로 시작/끝 금지: $value"
        }
    }

    override fun toString(): String = value

    companion object {
        private val VALID: Pattern = Pattern.compile("^[a-z0-9][a-z0-9-]{0,30}[a-z0-9]$")

        @JvmStatic
        fun of(value: String): TenantId = TenantId(value)
    }
}
