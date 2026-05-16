package com.example.security.application.exception

/**
 * 운영자가 호출한 use case 가 요구하는 role 을 보유하지 않은 경우.
 *
 * 예: `OPERATOR` role 만 가진 운영자가 admin endpoint (인덱스 rollover / tenant onboarding)
 * 를 호출한 경우. `com.example.security.adapter.in.exception.GlobalExceptionHandler` 가
 * HTTP 403 으로 매핑.
 */
class InsufficientPrivilegeException(private val required: String) :
    RuntimeException("권한 부족 — 요구 role: $required") {

    /** Java record-style accessor — adapter-in 에서 그대로 호출. */
    fun required(): String = required
}
