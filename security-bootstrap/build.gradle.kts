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
    // OpenAPI spec build-time export — generateOpenApiDocs 가 앱을 부팅한 뒤
    // /v3/api-docs 를 fetch 해 docs/openapi/security-log-search.yaml 로 떨어뜨린다.
    id("org.springdoc.openapi-gradle-plugin")
}

dependencies {
    implementation(project(":security-application"))
    implementation(project(":security-adapter-in"))
    implementation(project(":security-adapter-out"))

    // kotlin-reflect — Spring Data JPA 가 Kotlin @Entity data class 의 primary constructor 를
    // kotlin.reflect 로 탐색한다. kotlin.jvm 플러그인은 stdlib 만 classpath 에 올리므로 명시 필요.
    // 누락 시 컨텍스트 부팅에서 NoClassDefFoundError: kotlin/reflect/full/KClasses.
    implementation(kotlin("reflect"))

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
    implementation("org.apache.httpcomponents.core5:httpcore5:5.4.2")
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

// OpenAPI spec export 설정 — ./gradlew :security-bootstrap:generateOpenApiDocs.
// 플러그인이 bootRun 으로 앱을 띄우고 apiDocsUrl 을 fetch 해 outputFileName 으로 저장한다.
// 앱 부팅에 Postgres / Kafka / OpenSearch 가 필요하므로 로컬 단독 실행보다는 CI 에서
// docker compose 와 함께 돌리는 것을 권장 (docs/openapi/README.md 참고).
openApi {
    apiDocsUrl.set("http://localhost:8080/v3/api-docs.yaml")
    outputDir.set(layout.projectDirectory.dir("../docs/openapi"))
    outputFileName.set("security-log-search.yaml")
    waitTimeInSeconds.set(120)
}

// springdoc-openapi-gradle-plugin 의 forkedSpringBootRun 이 의존 모듈 jar 산출물을 명시적
// 의존성 없이 사용해 Gradle 8 validation 이 실패하던 문제 수정 — 각 모듈 jar 를 mustRunAfter
// 로 묶어 task graph 를 정합하게 만든다 (implicit_dependency validation 통과).
tasks.matching { it.name == "forkedSpringBootRun" }.configureEach {
    mustRunAfter(
        ":security-domain:jar",
        ":security-application:jar",
        ":security-adapter-in:jar",
        ":security-adapter-out:jar",
    )
    dependsOn(
        ":security-domain:jar",
        ":security-application:jar",
        ":security-adapter-in:jar",
        ":security-adapter-out:jar",
    )
}
