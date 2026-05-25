// Flink streaming job — 별도 jar (Spring Boot 안 들어감).
//
// 본 모듈은 Flink runtime 위에 띄우는 fat-jar 입니다. 도메인 모델 / AlertRule 정의는
// security-domain 을 그대로 가져오지만, 실제 실행은 LocalExecutionEnvironment (단위 테스트) 또는
// Flink 클러스터 (운영 jobmanager / taskmanager) 에서 동작합니다.
//
// 의존성 격리:
// - Spring Boot starter, OpenSearch / ClickHouse client 같은 무거운 의존성은 끌어오지 않음
// - Kafka client 는 Flink connector 가 자체적으로 가져옴 → spring-kafka 와 충돌 방지
//
// Kotlin 마이그레이션 — Flink ProcessFunction / serde 까지 Kotlin. Flink 직렬화는 Kotlin class
// (extends Java generic) 와 일반 class + Serializable 조합으로 보존된다. plugin.spring 은 필요 X
// (Flink runtime 은 Spring proxy 사용 안 함).
plugins {
    `java-library`
    kotlin("jvm")
}

val flinkVersion = "1.18.1"

dependencies {
    api(project(":security-domain"))

    // Flink core — provided (운영 환경에서는 클러스터에 이미 있음).
    compileOnly("org.apache.flink:flink-streaming-java:$flinkVersion")
    compileOnly("org.apache.flink:flink-clients:$flinkVersion")

    // 테스트 / local execution 용에는 runtime 으로 끌어옴.
    testImplementation("org.apache.flink:flink-streaming-java:$flinkVersion")
    testImplementation("org.apache.flink:flink-clients:$flinkVersion")
    testImplementation("org.apache.flink:flink-test-utils:$flinkVersion")
    testImplementation("org.apache.flink:flink-runtime:$flinkVersion:tests")
    testImplementation("org.apache.flink:flink-streaming-java:$flinkVersion:tests")

    // Kafka source / sink connector.
    compileOnly("org.apache.flink:flink-connector-kafka:3.2.0-1.18")
    compileOnly("org.apache.flink:flink-connector-base:2.2.1")
    testImplementation("org.apache.flink:flink-connector-kafka:3.2.0-1.18")
    testImplementation("org.apache.flink:flink-connector-base:2.2.1")

    // JSON 직렬화 — Flink 기본은 POJO serializer 라 record / nested type 은 Jackson 권장.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.junit.jupiter:junit-jupiter")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Flink + Java 17+ 모듈 시스템 — Kryo serializer 가 java.util / java.lang 의 private field 접근.
// 본 옵션은 Flink 운영 클러스터에서 실제 jobmanager / taskmanager 의 JVM_ARGS 로도 동일하게 추가 필요.
tasks.withType<Test> {
    jvmArgs(
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.time=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED")
}
