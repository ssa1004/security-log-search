package com.example.security.bootstrap.config;

import com.example.security.application.sigma.SigmaYamlParser;
import com.example.security.domain.sigma.SigmaToAlertRuleMapper;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Sigma 룰 import 관련 도메인 / application 컴포넌트 wiring. */
@Configuration
public class SigmaConfig {

  @Bean
  SigmaYamlParser sigmaYamlParser(Clock clock) {
    return new SigmaYamlParser(clock);
  }

  @Bean
  SigmaToAlertRuleMapper sigmaToAlertRuleMapper() {
    return new SigmaToAlertRuleMapper();
  }
}
