package com.example.security.application.port.out

import com.example.security.domain.rule.Alert

/**
 * 알람 발화 시 외부 통보 (notification-hub 같은 외부 시스템). 본 시스템은 fire-and-forget
 * 로 호출하고, 실패 시에도 알람 자체는 정상 저장된다.
 */
interface AlertNotificationPort {

    fun notify(alert: Alert)
}
