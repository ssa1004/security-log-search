// e2e — Testcontainers (Postgres + Kafka + OpenSearch + ClickHouse) 기반 통합 시나리오.
//
// 모든 테스트가 @Tag("integration") 으로 마킹되어 있어 기본 ./gradlew test 에서 제외됩니다.
// 실행: ./gradlew :e2e-tests:integrationTest (Docker 필요)
plugins {
    `java-library`
}

dependencies {
    testImplementation(project(":security-application"))
    testImplementation(project(":security-adapter-in"))
    testImplementation(project(":security-adapter-out"))
    testImplementation(project(":security-bootstrap"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:clickhouse")
    // OpenSearch testcontainer 는 module 이름이 다름.
    testImplementation("org.opensearch:opensearch-testcontainers:2.1.2")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.assertj:assertj-core")
}
