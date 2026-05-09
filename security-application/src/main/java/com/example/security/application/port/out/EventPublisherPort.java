package com.example.security.application.port.out;

import com.example.security.domain.event.LogEvent;

/** {@code events.normalized} Kafka topic 으로 정규화된 이벤트를 발행. */
public interface EventPublisherPort {

  void publish(LogEvent event);
}
