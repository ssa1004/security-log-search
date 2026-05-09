package com.example.security.adapter.in.rest.dto;

import com.example.security.domain.tenant.Tenant.PiiMaskingPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

public record TenantOnboardRequest(
    @NotBlank String tenantId,
    @NotBlank String displayName,
    @NotNull Duration retention,
    @NotNull Duration hotRetention,
    @NotNull PiiMaskingPolicy piiPolicy) {}
