package com.example.security.adapter.in.exception;

import com.example.security.application.exception.AlertNotFoundException;
import com.example.security.application.exception.RuleNotFoundException;
import com.example.security.application.exception.TenantMismatchException;
import com.example.security.application.exception.TenantNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 7807 (Problem Details) 형태의 에러 응답. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(TenantMismatchException.class)
  public ResponseEntity<Map<String, Object>> tenantMismatch(TenantMismatchException e) {
    return problem(HttpStatus.FORBIDDEN, "tenant_mismatch", e.getMessage());
  }

  @ExceptionHandler(TenantNotFoundException.class)
  public ResponseEntity<Map<String, Object>> tenantNotFound(TenantNotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "tenant_not_found", e.getMessage());
  }

  @ExceptionHandler({AlertNotFoundException.class, RuleNotFoundException.class})
  public ResponseEntity<Map<String, Object>> notFound(RuntimeException e) {
    return problem(HttpStatus.NOT_FOUND, "not_found", e.getMessage());
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
  public ResponseEntity<Map<String, Object>> badRequest(Exception e) {
    return problem(HttpStatus.BAD_REQUEST, "bad_request", e.getMessage());
  }

  private static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String type, String detail) {
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "type", type,
                "title", status.getReasonPhrase(),
                "status", status.value(),
                "detail", detail == null ? "" : detail,
                "timestamp", Instant.now().toString()));
  }
}
