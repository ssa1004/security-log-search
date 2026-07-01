package com.example.security.adapter.out.opensearch

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * [OpenSearchEventSearchAdapter] 의 field 화이트리스트 검증 단위 테스트.
 *
 * OpenSearchClient 자체 호출 경로 (검색 결과 mapping) 는 통합 테스트 (e2e-tests 모듈) 에서
 * Testcontainers 로 검증한다. 본 테스트는 termFilters / facet 의 field 이름에 임의 값이
 * 들어왔을 때 차단되는지 — 멀티테넌트 격리의 (a) 인덱스 alias / (d) tenant filter 외에
 * 추가로 사용자 입력 field 를 봉쇄하는 layer — 만 회귀 락한다.
 */
class OpenSearchEventSearchAdapterTest {

    @Test
    fun `화이트리스트 필드는 그대로 통과`() {
        val allowed = arrayOf(
            "event_kind",
            "event_category",
            "event_action",
            "event_outcome",
            "severity",
            "source_ip",
            "destination_ip",
            "user_name",
            "host_name",
            "host_os",
            "event_type",
        )
        for (a in allowed) {
            assertThat(OpenSearchEventSearchAdapter.requireAllowedField(a)).isEqualTo(a)
        }
    }

    @Test
    fun `tenant_id 는 termFilters field 로 허용되지 않는다`() {
        // tenant_id 는 SearchService 가 강제 주입하는 격리 필드 — 사용자 입력으로 한 번 더
        // 들어와 의도치 않은 결과 (예: 다른 값으로 OR override) 를 만들 수 없도록 차단.
        assertThatThrownBy { OpenSearchEventSearchAdapter.requireAllowedField("tenant_id") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("tenant_id")
    }

    @Test
    fun `OpenSearch 내부 메타데이터 필드는 차단`() {
        for (meta in arrayOf("_id", "_index", "_source", "_routing")) {
            assertThatThrownBy { OpenSearchEventSearchAdapter.requireAllowedField(meta) }
                .`as`("내부 필드 %s 는 차단되어야 함", meta)
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `mapping 되지 않은 임의 필드는 차단`() {
        assertThatThrownBy { OpenSearchEventSearchAdapter.requireAllowedField("admin_only") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { OpenSearchEventSearchAdapter.requireAllowedField("foo.bar") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `null 이나 빈문자열도 차단`() {
        assertThatThrownBy { OpenSearchEventSearchAdapter.requireAllowedField(null) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { OpenSearchEventSearchAdapter.requireAllowedField("") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
