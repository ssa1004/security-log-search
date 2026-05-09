// Application — use case + port (in / out 인터페이스). 도메인에만 의존.
plugins {
    `java-library`
}

dependencies {
    api(project(":security-domain"))

    // @Service / @Transactional 같은 Spring 어노테이션을 use case 에 부착하지만 구현체는 bootstrap 에서 wiring.
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
    implementation("jakarta.validation:jakarta.validation-api")

    // Resilience4j fallback 어노테이션 — 외부 호출 (OpenSearch / ClickHouse) 보호.
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}
