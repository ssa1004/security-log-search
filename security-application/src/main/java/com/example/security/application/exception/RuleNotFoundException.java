package com.example.security.application.exception;

import java.util.UUID;

public class RuleNotFoundException extends RuntimeException {

  public RuleNotFoundException(UUID ruleId) {
    super("알람 룰을 찾을 수 없음: " + ruleId);
  }
}
