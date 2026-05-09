package com.example.security.adapter.in.rest.controller;

import com.example.security.adapter.in.metrics.SecurityLogMetrics;
import com.example.security.adapter.in.rest.dto.IngestRequest;
import com.example.security.adapter.in.rest.dto.IngestResponse;
import com.example.security.application.port.in.IngestLogEventUseCase;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.RawEvent;
import com.example.security.domain.mapping.EventNormalizer;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** use case 1 — POST /api/v1/events. */
@RestController
@RequestMapping("/api/v1/events")
public class IngestController {

  private final IngestLogEventUseCase useCase;
  private final Clock clock;
  private final SecurityLogMetrics metrics;

  public IngestController(IngestLogEventUseCase useCase, Clock clock, SecurityLogMetrics metrics) {
    this.useCase = useCase;
    this.clock = clock;
    this.metrics = metrics;
  }

  @PostMapping
  public ResponseEntity<IngestResponse> ingest(
      @Valid @RequestBody IngestRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    var raw =
        new RawEvent(
            TenantId.of(request.tenantId()),
            request.occurredAt() != null ? request.occurredAt() : clock.instant(),
            request.source(),
            request.schema(),
            request.payload());
    try {
      var result = useCase.ingest(raw, idempotencyKey);
      metrics.recordIngest(request.source(), request.tenantId(), request.schema());
      var status = result.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
      return ResponseEntity.status(status).body(new IngestResponse(result.eventId(), result.duplicate()));
    } catch (EventNormalizer.UnsupportedSchemaException e) {
      metrics.recordNormalizeFailure(request.source(), request.schema(), "unsupported_schema");
      throw e;
    } catch (IllegalArgumentException e) {
      metrics.recordNormalizeFailure(request.source(), request.schema(), "validation_failed");
      throw e;
    }
  }
}
