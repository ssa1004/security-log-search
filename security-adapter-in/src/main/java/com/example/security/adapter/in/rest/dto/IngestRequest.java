package com.example.security.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/** raw event 수집 요청. */
public record IngestRequest(
    @NotBlank String tenantId,
    @NotBlank String source,
    @NotBlank String schema,
    @NotNull Map<String, Object> payload,
    /** 클라이언트가 보고한 timestamp (없으면 서버 수신 시각 사용). */
    Instant occurredAt) {}
