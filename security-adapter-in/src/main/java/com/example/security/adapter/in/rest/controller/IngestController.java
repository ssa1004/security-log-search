package com.example.security.adapter.in.rest.controller;

import com.example.security.adapter.in.rest.dto.IngestRequest;
import com.example.security.adapter.in.rest.dto.IngestResponse;
import com.example.security.application.port.in.IngestLogEventUseCase;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.RawEvent;
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

  public IngestController(IngestLogEventUseCase useCase, Clock clock) {
    this.useCase = useCase;
    this.clock = clock;
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
    var result = useCase.ingest(raw, idempotencyKey);
    var status = result.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
    return ResponseEntity.status(status).body(new IngestResponse(result.eventId(), result.duplicate()));
  }
}
