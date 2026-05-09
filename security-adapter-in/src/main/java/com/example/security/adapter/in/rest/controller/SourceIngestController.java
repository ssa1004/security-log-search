package com.example.security.adapter.in.rest.controller;

import com.example.security.adapter.in.metrics.SecurityLogMetrics;
import com.example.security.adapter.in.rest.dto.IngestResponse;
import com.example.security.adapter.in.rest.dto.SourceIngestRequest;
import com.example.security.application.port.in.IngestLogEventUseCase;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.RawEvent;
import com.example.security.domain.mapping.EventNormalizer;
import com.example.security.domain.mapping.source.CloudTrailToEcsMapper;
import com.example.security.domain.mapping.source.K8sAuditToEcsMapper;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * source 전용 ingest endpoint — schema 를 endpoint path 로 고정한다.
 *
 * <p>일반 {@code POST /api/v1/events} 는 {@link com.example.security.adapter.in.rest.dto.IngestRequest}
 * 의 {@code schema} 필드로 매퍼를 라우팅하지만, CloudTrail / K8s audit 처럼 *공급원 자체가 schema*
 * 인 경우는 endpoint 를 분리하면 클라이언트가 더 단순하다 (수집 agent 가 endpoint 만 알면 됨).
 *
 * <ul>
 *   <li>{@code POST /api/v1/events/cloudtrail} → schema={@code aws-cloudtrail}
 *   <li>{@code POST /api/v1/events/k8s-audit}   → schema={@code k8s-audit}
 * </ul>
 *
 * <p>실제 정규화는 {@link com.example.security.application.service.IngestLogEventService} 가 schema
 * 를 보고 적절한 매퍼로 라우팅한다.
 */
@RestController
@RequestMapping("/api/v1/events")
public class SourceIngestController {

  private final IngestLogEventUseCase useCase;
  private final Clock clock;
  private final SecurityLogMetrics metrics;

  public SourceIngestController(
      IngestLogEventUseCase useCase, Clock clock, SecurityLogMetrics metrics) {
    this.useCase = useCase;
    this.clock = clock;
    this.metrics = metrics;
  }

  @PostMapping("/cloudtrail")
  public ResponseEntity<IngestResponse> cloudtrail(
      @Valid @RequestBody SourceIngestRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return ingestWithSchema(request, CloudTrailToEcsMapper.SCHEMA, "aws", idempotencyKey);
  }

  @PostMapping("/k8s-audit")
  public ResponseEntity<IngestResponse> k8sAudit(
      @Valid @RequestBody SourceIngestRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return ingestWithSchema(request, K8sAuditToEcsMapper.SCHEMA, "k8s", idempotencyKey);
  }

  private ResponseEntity<IngestResponse> ingestWithSchema(
      SourceIngestRequest request, String schema, String source, String idempotencyKey) {
    var raw =
        new RawEvent(
            TenantId.of(request.tenantId()),
            request.occurredAt() != null ? request.occurredAt() : clock.instant(),
            source,
            schema,
            request.payload());
    try {
      var result = useCase.ingest(raw, idempotencyKey);
      metrics.recordIngest(source, request.tenantId(), schema);
      var status = result.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
      return ResponseEntity.status(status).body(new IngestResponse(result.eventId(), result.duplicate()));
    } catch (EventNormalizer.UnsupportedSchemaException e) {
      metrics.recordNormalizeFailure(source, schema, "unsupported_schema");
      throw e;
    } catch (IllegalArgumentException e) {
      metrics.recordNormalizeFailure(source, schema, "validation_failed");
      throw e;
    }
  }
}
