package com.example.security.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/**
 * source 별 raw payload ingest 요청 — CloudTrail / K8s audit 같이 schema 가 endpoint 로 고정된 경우.
 *
 * <p>{@link IngestRequest} 와 달리 {@code schema} 필드가 없다 — endpoint 가 schema 를 결정.
 *
 * @param tenantId 테넌트 식별자
 * @param payload raw event 본문 (JSON object — 그대로 매퍼에 전달)
 * @param occurredAt 클라이언트가 보고한 timestamp (없으면 서버 수신 시각)
 */
public record SourceIngestRequest(
    @NotBlank String tenantId, @NotNull Map<String, Object> payload, Instant occurredAt) {}
