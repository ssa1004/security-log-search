package com.example.security.adapter.in.rest.dto;

import com.example.security.domain.tenant.Tenant;
import java.time.Duration;
import java.time.Instant;

public record TenantResponse(
    String tenantId,
    String displayName,
    Duration retention,
    Duration hotRetention,
    String piiPolicy,
    Instant onboardedAt,
    boolean active) {

  public static TenantResponse from(Tenant t) {
    return new TenantResponse(
        t.tenantId().value(),
        t.displayName(),
        t.retention(),
        t.hotRetention(),
        t.piiPolicy().name(),
        t.onboardedAt(),
        t.active());
  }
}
