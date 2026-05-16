// Inbound adapter — REST controller + Kafka consumer (alerts.fired) + Swagger UI.
//
// Kotlin 마이그레이션 — controller / consumer / DTO 까지 Kotlin. plugin.spring 이 @Controller /
// @Component 가 붙은 class 를 자동으로 open 처리해 CGLIB proxy 를 생성 가능하게 한다.
plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
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
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    // Kotlin null-safety 와 호환되는 Jackson module — Jackson 이 Kotlin data class 의
    // non-null 필드를 인식해야 LocalDateTime / Instant / Enum 역직렬화가 정상 동작한다.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Mockito Kotlin helpers — any() / whenever / verify 의 Kotlin friendly DSL.
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
