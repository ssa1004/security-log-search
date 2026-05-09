package com.example.security.bootstrap.config;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "security.opensearch.enabled", havingValue = "true", matchIfMissing = false)
public class OpenSearchConfig {

  @Bean
  OpenSearchClient openSearchClient(
      @Value("${security.opensearch.host:localhost}") String host,
      @Value("${security.opensearch.port:9200}") int port,
      @Value("${security.opensearch.scheme:http}") String scheme) {
    OpenSearchTransport transport =
        ApacheHttpClient5TransportBuilder.builder(new HttpHost(scheme, host, port))
            .setMapper(new JacksonJsonpMapper())
            .build();
    return new OpenSearchClient(transport);
  }
}
