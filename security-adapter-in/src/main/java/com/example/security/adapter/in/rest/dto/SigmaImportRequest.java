package com.example.security.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * POST /api/v1/sigma-rules — Sigma YAML import 요청.
 *
 * @param tenantId import 대상 tenant
 * @param yaml Sigma YAML — 단일 또는 multi-document
 * @param overwriteByTitle 같은 sigma_id 가 이미 import 된 경우 덮어쓸지 여부
 */
public record SigmaImportRequest(
    @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,30}[a-z0-9]$") String tenantId,
    @NotBlank String yaml,
    boolean overwriteByTitle) {}
