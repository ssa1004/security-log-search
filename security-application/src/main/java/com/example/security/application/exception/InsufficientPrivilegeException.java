package com.example.security.application.exception;

/**
 * 운영자가 호출한 use case 가 요구하는 role 을 보유하지 않은 경우.
 *
 * <p>예: {@code OPERATOR} role 만 가진 운영자가 admin endpoint (인덱스 rollover / tenant
 * onboarding) 를 호출한 경우. {@link com.example.security.adapter.in.exception.GlobalExceptionHandler}
 * 가 HTTP 403 으로 매핑.
 */
public class InsufficientPrivilegeException extends RuntimeException {

  private final String required;

  public InsufficientPrivilegeException(String required) {
    super("권한 부족 — 요구 role: " + required);
    this.required = required;
  }

  public String required() {
    return required;
  }
}
