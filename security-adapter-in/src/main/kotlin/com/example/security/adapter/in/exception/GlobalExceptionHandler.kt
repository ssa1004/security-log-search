package com.example.security.adapter.`in`.exception

import com.example.security.application.exception.AlertNotFoundException
import com.example.security.application.exception.InsufficientPrivilegeException
import com.example.security.application.exception.RuleNotFoundException
import com.example.security.application.exception.TenantMismatchException
import com.example.security.application.exception.TenantNotFoundException
import jakarta.validation.ConstraintViolationException
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** RFC 7807 (Problem Details) 형태의 에러 응답. */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(TenantMismatchException::class)
    fun tenantMismatch(e: TenantMismatchException): ResponseEntity<Map<String, Any>> =
        problem(HttpStatus.FORBIDDEN, "tenant_mismatch", e.message)

    @ExceptionHandler(InsufficientPrivilegeException::class)
    fun insufficientPrivilege(e: InsufficientPrivilegeException): ResponseEntity<Map<String, Any>> =
        problem(HttpStatus.FORBIDDEN, "insufficient_privilege", e.message)

    @ExceptionHandler(TenantNotFoundException::class)
    fun tenantNotFound(e: TenantNotFoundException): ResponseEntity<Map<String, Any>> =
        problem(HttpStatus.NOT_FOUND, "tenant_not_found", e.message)

    @ExceptionHandler(AlertNotFoundException::class, RuleNotFoundException::class)
    fun notFound(e: RuntimeException): ResponseEntity<Map<String, Any>> =
        problem(HttpStatus.NOT_FOUND, "not_found", e.message)

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        ConstraintViolationException::class,
        IllegalArgumentException::class,
    )
    fun badRequest(e: Exception): ResponseEntity<Map<String, Any>> =
        problem(HttpStatus.BAD_REQUEST, "bad_request", e.message)

    private fun problem(
        status: HttpStatus,
        type: String,
        detail: String?,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(status).body(
            mapOf(
                "type" to type,
                "title" to status.reasonPhrase,
                "status" to status.value(),
                "detail" to (detail ?: ""),
                "timestamp" to Instant.now().toString(),
            ),
        )
}
