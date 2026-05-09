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

    // OpenAPI / Swagger UI.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
