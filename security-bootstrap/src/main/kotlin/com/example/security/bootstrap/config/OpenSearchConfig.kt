package com.example.security.bootstrap.config

import org.apache.hc.core5.http.HttpHost
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["security.opensearch.enabled"], havingValue = "true", matchIfMissing = false)
class OpenSearchConfig {

    @Bean
    fun openSearchClient(
        @Value("\${security.opensearch.host:localhost}") host: String,
        @Value("\${security.opensearch.port:9200}") port: Int,
        @Value("\${security.opensearch.scheme:http}") scheme: String,
    ): OpenSearchClient {
        val transport =
            ApacheHttpClient5TransportBuilder.builder(HttpHost(scheme, host, port))
                .setMapper(JacksonJsonpMapper())
                .build()
        return OpenSearchClient(transport)
    }
}
