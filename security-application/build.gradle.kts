// Application — use case + port (in / out 인터페이스). 도메인에만 의존.
//
// Kotlin 마이그레이션: domain (이미 Kotlin) 위의 application layer 도 Kotlin 으로 옮긴다.
// adapter-in / adapter-out / streaming / bootstrap / e2e 6 모듈은 Java 유지하므로
// record-style accessor (`tenantId()`, `value()` 등) 와 SAM interface 호환이 핵심이다.
plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(project(":security-domain"))

    // @Service / @Transactional 같은 Spring 어노테이션을 use case 에 부착하지만 구현체는 bootstrap 에서 wiring.
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("org.slf4j:slf4j-api")

    // Resilience4j fallback 어노테이션 — 외부 호출 (OpenSearch / ClickHouse) 보호.
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.4.0")

    // Sigma 룰 import — SigmaHQ 의 룰 포맷이 YAML.
    implementation("org.yaml:snakeyaml")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

kotlin {
    // Java toolchain 과 동일하게 JVM 21 — @JvmRecord 는 JVM 16+ 필요.
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        // record-style accessor / @JvmRecord 지원에 필요한 최신 타깃.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
