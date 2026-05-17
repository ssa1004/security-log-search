package com.example.security.bootstrap.config

import com.example.security.application.sigma.SigmaYamlParser
import com.example.security.domain.sigma.SigmaToAlertRuleMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Sigma 룰 import 관련 도메인 / application 컴포넌트 wiring. */
@Configuration
class SigmaConfig {

    @Bean
    fun sigmaYamlParser(clock: Clock): SigmaYamlParser = SigmaYamlParser(clock)

    @Bean
    fun sigmaToAlertRuleMapper(): SigmaToAlertRuleMapper = SigmaToAlertRuleMapper()
}
