package com.example.security.adapter.`in`.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    /** 운영 — JWT Resource Server. */
    @Bean
    @Profile("prod")
    fun prod(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(
                    "/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**",
                    "/swagger",
                ).permitAll()
                    .anyRequest()
                    .authenticated()
            }
            .oauth2ResourceServer { oauth -> oauth.jwt(Customizer.withDefaults()) }
        return http.build()
    }

    /** local / dev / test — 모두 통과. dev fallback OperatorContext 사용. */
    @Bean
    @Profile("!prod")
    fun dev(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth -> auth.anyRequest().permitAll() }
        return http.build()
    }

    /** 사용된다면 — 운영용 issuer URI. */
    @Bean
    @Profile("prod")
    fun oauthIssuerInfo(
        @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") issuer: String,
    ): String = issuer
}
