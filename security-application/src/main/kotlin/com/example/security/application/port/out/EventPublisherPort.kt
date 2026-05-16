package com.example.security.application.port.out

import com.example.security.domain.event.LogEvent

/** `events.normalized` Kafka topic 으로 정규화된 이벤트를 발행. */
interface EventPublisherPort {

    fun publish(event: LogEvent)
}
