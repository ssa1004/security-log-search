package com.example.security.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/sigma-rules — Sigma YAML import 요청.
 *
 * @param tenantId import 대상 tenant
 * @param yaml Sigma YAML — 단일 또는 multi-document. 5 MB 상한 (API4 — 자원 소비 제한, YAML
 *     parser DoS 방지).
 * @param overwriteByTitle 같은 sigma_id 가 이미 import 된 경우 덮어쓸지 여부
 */
public record SigmaImportRequest(
    @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,30}[a-z0-9]$") String tenantId,
    @NotBlank @Size(max = 5_242_880) String yaml,
    boolean overwriteByTitle) {}
