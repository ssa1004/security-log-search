// Inbound adapter — REST controller + Kafka consumer (alerts.fired) + Swagger UI.
plugins {
    `java-library`
}

dependencies {
    implementation(project(":security-application"))
    implementation(project(":security-adapter-out"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.kafka:spring-kafka")

    // Micrometer — controller / consumer 의 운영 메트릭 (RED 모델). 실제 PrometheusMeterRegistry
    // 는 bootstrap 모듈에서 노출하며 본 모듈은 인터페이스 (MeterRegistry) 만 의존한다.
    implementation("io.micrometer:micrometer-core")

    // OpenAPI / Swagger UI.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
