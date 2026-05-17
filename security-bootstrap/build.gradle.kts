// Bootstrap — Spring Boot main + application.yml + Flyway 마이그레이션.
//
// Kotlin 마이그레이션 — main + @Configuration / @Bean wiring 까지 Kotlin. plugin.spring 이
// @Configuration / @SpringBootApplication class 를 자동 open 처리 → Spring proxy / CGLIB 정상 동작.
plugins {
    java
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":security-application"))
    implementation(project(":security-adapter-in"))
    implementation(project(":security-adapter-out"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-core")
    implementation("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // OpenSearch / ClickHouse 클라이언트는 conditional 로 켜지지만 컴파일 시점에는 필요.
    // adapter-out 과 동일 버전 사용 — 두 모듈이 다른 라인이면 transport 충돌.
    implementation("org.opensearch.client:opensearch-java:2.18.0")
    implementation("org.opensearch.client:opensearch-rest-client:2.18.0")
    // OpenSearch transport 가 의존하는 httpcore5 — Spring Boot 가 직접 관리하지 않음.
    implementation("org.apache.httpcomponents.core5:httpcore5:5.2.5")
    implementation("com.clickhouse:clickhouse-jdbc:0.6.5:all")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}

kotlin {
    // Java toolchain 과 동일하게 JVM 21.
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

springBoot {
    mainClass.set("com.example.security.SecurityLogSearchApplication")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveClassifier.set("boot")
    enabled = true
}

tasks.named<Jar>("jar") {
    enabled = true
}
