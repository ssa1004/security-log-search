package com.example.security.application.exception

import java.util.UUID

class RuleNotFoundException(ruleId: UUID) : RuntimeException("알람 룰을 찾을 수 없음: $ruleId")
