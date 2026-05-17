// Outbound adapter — JPA (control plane: tenants / alert_rules / alerts / audit_entries)
//                  + Kafka producer (events.normalized, alerts.fired)
//                  + OpenSearch client (Java client 2.x)
//                  + ClickHouse JDBC.
//
// Kotlin 마이그레이션 — entity / repository / mapper / 외부 client 모두 Kotlin. plugin.spring 은
// @Repository / @Component 의 open 처리, plugin.jpa 는 @Entity 의 no-arg constructor 합성을 담당.
plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
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
    // java client 와 rest client 는 동일 라인으로 정렬 — 2.10 ↔ 2.18 mismatch 시 transport 호환성 이슈.
    implementation("org.opensearch.client:opensearch-java:2.18.0")
    implementation("org.opensearch.client:opensearch-rest-client:2.18.0")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("jakarta.json:jakarta.json-api:2.1.3")
    runtimeOnly("org.eclipse.parsson:parsson:1.1.7")

    // ClickHouse JDBC — aggregate / 시계열 query.
    implementation("com.clickhouse:clickhouse-jdbc:0.9.8:all")
    implementation("com.clickhouse:clickhouse-http-client:0.9.8")

    // Resilience4j — 외부 호출 보호 (CB + Retry + Bulkhead). (ADR-0009 참고)
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.4.0")

    // Messaging — Kafka producer / consumer.
    implementation("org.springframework.kafka:spring-kafka")

    // Tracing — Micrometer.
    implementation("io.micrometer:micrometer-tracing")

    // Kotlin null-safety 와 호환되는 Jackson module — OpenSearch / ClickHouse / Kafka 직렬화 시
    // Kotlin data class 의 nullability / default value 를 인식한다.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.assertj:assertj-core")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
