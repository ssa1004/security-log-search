package com.example.security.application.exception;

import com.example.security.domain.common.TenantId;

public class TenantNotFoundException extends RuntimeException {

  public TenantNotFoundException(TenantId tenantId) {
    super("테넌트를 찾을 수 없음: " + tenantId.value());
  }
}
