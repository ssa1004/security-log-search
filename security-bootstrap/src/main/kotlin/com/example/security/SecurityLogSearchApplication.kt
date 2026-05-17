package com.example.security

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ComponentScan(basePackages = ["com.example.security"])
@EntityScan(basePackages = ["com.example.security.adapter.out.jpa.entity"])
@EnableJpaRepositories(basePackages = ["com.example.security.adapter.out.jpa.repository"])
@EnableScheduling
class SecurityLogSearchApplication

fun main(args: Array<String>) {
    SpringApplication.run(SecurityLogSearchApplication::class.java, *args)
}
