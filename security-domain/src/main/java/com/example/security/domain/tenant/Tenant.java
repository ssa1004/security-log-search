package com.example.security.domain.tenant;

import com.example.security.domain.common.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 테넌트 — 본 시스템의 격리 단위.
 *
 * <p>onboarding 시 OpenSearch alias / ClickHouse Row Policy / 인덱스 템플릿이 자동 wiring 된다.
 * 테넌트마다 보존 기간 / hot tier 일수 등을 다르게 설정 가능.
 */
public record Tenant(
    TenantId tenantId,
    String displayName,
    /** 데이터 보존 기간 — 이 기간 지난 인덱스는 ILM 이 삭제. ISMS-P 권고 최소 1년. */
    Duration retention,
    /** hot tier 유지 기간 — SSD 노드. 운영 트래픽이 많으면 늘림. */
    Duration hotRetention,
    /** PII 마스킹 정책 — 운영자 role 별 view 적용 여부. */
    PiiMaskingPolicy piiPolicy,
    Instant onboardedAt,
    boolean active) {

  public Tenant {
    Objects.requireNonNull(tenantId);
    Objects.requireNonNull(displayName);
    Objects.requireNonNull(retention);
    Objects.requireNonNull(hotRetention);
    Objects.requireNonNull(piiPolicy);
    Objects.requireNonNull(onboardedAt);
    if (retention.compareTo(Duration.ofDays(365)) < 0) {
      throw new IllegalArgumentException("ISMS-P 권고 — 보안 로그 보존 최소 1년: " + retention);
    }
    if (hotRetention.compareTo(retention) > 0) {
      throw new IllegalArgumentException("hot retention 은 전체 retention 이하");
    }
  }

  /** OpenSearch read alias. */
  public String readAlias() {
    return "events-%s-read".formatted(tenantId.value());
  }

  /** OpenSearch write alias. */
  public String writeAlias() {
    return "events-%s-write".formatted(tenantId.value());
  }

  /** OpenSearch ILM policy 이름. */
  public String ilmPolicyName() {
    return "ilm-events-%s".formatted(tenantId.value());
  }

  public enum PiiMaskingPolicy {
    /** 마스킹 안 함 (개발용). */
    NONE,
    /** IP 주소 마스킹 (마지막 옥텟). */
    IP_ONLY,
    /** IP + 사용자명 + 이메일 마스킹. */
    STRICT
  }
}
