package com.example.security.bootstrap.config

import com.example.security.domain.mapping.EventNormalizer
import com.example.security.domain.mapping.RoutingNormalizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NormalizerConfig {

    @Bean
    fun eventNormalizer(): EventNormalizer = RoutingNormalizer()
}
