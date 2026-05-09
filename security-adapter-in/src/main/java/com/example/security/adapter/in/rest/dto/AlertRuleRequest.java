package com.example.security.adapter.in.rest.dto;

import com.example.security.domain.common.Severity;
import com.example.security.domain.rule.AlertRule.RuleType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

public record AlertRuleRequest(
    @NotBlank String tenantId,
    @NotBlank String name,
    String description,
    @NotNull RuleType type,
    String filterCategory,
    String filterAction,
    String filterOutcome,
    @NotBlank String groupByField,
    @Min(1) int threshold,
    @NotNull Duration window,
    @NotNull Severity severity,
    boolean enabled) {}
