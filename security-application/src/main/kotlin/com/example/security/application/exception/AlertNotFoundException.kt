package com.example.security.application.exception

import java.util.UUID

class AlertNotFoundException(alertId: UUID) : RuntimeException("알람을 찾을 수 없음: $alertId")
