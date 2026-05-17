package com.example.security.bootstrap.config

import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 시간 의존성을 명시적으로 — 테스트 시 Clock.fixed() 로 대체 가능. */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
