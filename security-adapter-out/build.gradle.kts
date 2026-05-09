// Outbound adapter — JPA (control plane: tenants / alert_rules / alerts / audit_entries)
//                  + Kafka producer (events.normalized, alerts.fired)
//                  + OpenSearch client (Java client 2.x)
//                  + ClickHouse JDBC.
plugins {
    `java-library`
}

dependencies {
    implementation(project(":security-application"))

    // Persistence — control plane.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // OpenSearch Java Client 2.x — Spring Data Elasticsearch 대신 low-level client.
    // OpenSearch 의 query DSL / index template / ILM 호출이 더 정확. (ADR-0002, ADR-0006 참고)
    implementation("org.opensearch.client:opensearch-java:2.10.4")
    implementation("org.opensearch.client:opensearch-rest-client:2.18.0")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("jakarta.json:jakarta.json-api:2.1.3")
    runtimeOnly("org.eclipse.parsson:parsson:1.1.6")

    // ClickHouse JDBC — aggregate / 시계열 query.
    implementation("com.clickhouse:clickhouse-jdbc:0.6.5:all")
    implementation("com.clickhouse:clickhouse-http-client:0.6.5")

    // Resilience4j — 외부 호출 보호 (CB + Retry + Bulkhead). (ADR-0009 참고)
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    // Messaging — Kafka producer / consumer.
    implementation("org.springframework.kafka:spring-kafka")

    // Tracing — Micrometer.
    implementation("io.micrometer:micrometer-tracing")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.assertj:assertj-core")
}
