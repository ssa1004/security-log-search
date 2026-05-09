package com.example.security.application.exception;

import java.util.UUID;

public class AlertNotFoundException extends RuntimeException {

  public AlertNotFoundException(UUID alertId) {
    super("알람을 찾을 수 없음: " + alertId);
  }
}
