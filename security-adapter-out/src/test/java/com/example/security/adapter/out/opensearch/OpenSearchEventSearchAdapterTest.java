package com.example.security.adapter.out.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link OpenSearchEventSearchAdapter} 의 field 화이트리스트 검증 단위 테스트.
 *
 * <p>OpenSearchClient 자체 호출 경로 (검색 결과 mapping) 는 통합 테스트 (e2e-tests 모듈) 에서
 * Testcontainers 로 검증한다. 본 테스트는 termFilters / facet 의 field 이름에 임의 값이
 * 들어왔을 때 차단되는지 — 멀티테넌트 격리의 (a) 인덱스 alias / (d) tenant filter 외에
 * 추가로 사용자 입력 field 를 봉쇄하는 layer — 만 회귀 락한다.
 */
class OpenSearchEventSearchAdapterTest {

  @Test
  void 화이트리스트_필드는_그대로_통과() {
    for (var allowed :
        new String[] {
          "event_kind",
          "event_category",
          "event_action",
          "event_outcome",
          "severity",
          "source_ip",
          "destination_ip",
          "user_name",
          "host_name",
          "host_os"
        }) {
      assertThat(OpenSearchEventSearchAdapter.requireAllowedField(allowed)).isEqualTo(allowed);
    }
  }

  @Test
  void tenant_id_는_termFilters_field_로_허용되지_않는다() {
    // tenant_id 는 SearchService 가 강제 주입하는 격리 필드 — 사용자 입력으로 한 번 더
    // 들어와 의도치 않은 결과 (예: 다른 값으로 OR override) 를 만들 수 없도록 차단.
    assertThatThrownBy(() -> OpenSearchEventSearchAdapter.requireAllowedField("tenant_id"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenant_id");
  }

  @Test
  void OpenSearch_내부_메타데이터_필드는_차단() {
    for (var meta : new String[] {"_id", "_index", "_source", "_routing"}) {
      assertThatThrownBy(() -> OpenSearchEventSearchAdapter.requireAllowedField(meta))
          .as("내부 필드 %s 는 차단되어야 함", meta)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void mapping_되지_않은_임의_필드는_차단() {
    assertThatThrownBy(() -> OpenSearchEventSearchAdapter.requireAllowedField("admin_only"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> OpenSearchEventSearchAdapter.requireAllowedField("foo.bar"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void null_이나_빈문자열도_차단() {
    assertThatThrownBy(() -> OpenSearchEventSearchAdapter.requireAllowedField(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> OpenSearchEventSearchAdapter.requireAllowedField(""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
