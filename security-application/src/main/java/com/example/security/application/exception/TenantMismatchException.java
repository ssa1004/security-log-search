package com.example.security.application.exception;

import com.example.security.domain.common.TenantId;

/**
 * 운영자의 tenantId 와 요청 query 의 tenantId 가 다른 경우.
 *
 * <p>일반 운영자가 다른 tenant 의 데이터에 접근하려는 시도 — 즉시 거부 + audit 기록.
 */
public class TenantMismatchException extends RuntimeException {

  public TenantMismatchException(TenantId operator, TenantId requested) {
    super("운영자 tenant (%s) 와 요청 tenant (%s) 불일치".formatted(operator.value(), requested.value()));
  }
}
