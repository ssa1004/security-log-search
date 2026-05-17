package com.example.security.bootstrap.config

import com.clickhouse.jdbc.ClickHouseDataSource
import java.util.Properties
import javax.sql.DataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["security.clickhouse.enabled"], havingValue = "true", matchIfMissing = false)
class ClickHouseConfig {

    @Bean(name = ["clickHouseDataSource"])
    fun clickHouseDataSource(
        @Value("\${security.clickhouse.url:jdbc:clickhouse://localhost:8123/default}") url: String,
        @Value("\${security.clickhouse.user:default}") user: String,
        @Value("\${security.clickhouse.password:}") password: String,
    ): DataSource {
        val props = Properties()
        props.setProperty("user", user)
        props.setProperty("password", password)
        return ClickHouseDataSource(url, props)
    }
}
