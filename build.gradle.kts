// 루트 빌드 — 공통 conventions. 각 모듈이 상속받는 공유 설정.
//
// security-streaming 은 다른 6 모듈과 다르게 Spring Boot 가 아닌 Apache Flink runtime 위에서
// 실행되는 별도 jar 입니다. dependency-management BOM 까지는 공유하되 Spring Boot starter
// 같은 의존성은 각 모듈 build.gradle.kts 에서 명시적으로만 선언합니다.
plugins {
    java
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    // OpenAPI spec build-time export — 실제 적용은 security-bootstrap 모듈.
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0" apply false
    // 전 모듈 (domain / application / adapter / streaming / bootstrap / e2e) 이 Kotlin.
    // 적용은 각 모듈 build.gradle.kts 에서만. 버전은 Gradle 8.10 번들 Kotlin 과 정렬.
    kotlin("jvm") version "1.9.24" apply false
    // plugin.spring — @Component / @Controller / @Service 등 Spring 어노테이션 class 를 자동
    //                  open 처리해 CGLIB proxy 가능하게 한다. adapter-in / adapter-out 에 적용.
    kotlin("plugin.spring") version "1.9.24" apply false
    // plugin.jpa — @Entity 가 붙은 class 에 no-arg constructor 합성. adapter-out 만 사용.
    kotlin("plugin.jpa") version "1.9.24" apply false
    // Kover — Kotlin-native 코드 커버리지. 프로덕션 소스가 100% Kotlin 이라 JaCoCo 대신 Kover
    //         를 쓴다. 루트에 적용하면 koverHtmlReport / koverXmlReport 가 하위 모듈 (kover
    //         적용된) 커버리지를 집계한다.
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

allprojects {
    group = "com.example.security"
    version = "0.1.0"

    repositories {
        mavenCentral()
        // OpenSearch / Flink 등 일부 아티팩트가 Maven Central 외 저장소에 있을 가능성 대비.
        maven { url = uri("https://repo1.maven.org/maven2/") }
    }
}

// Kover 집계 대상 — 단위 테스트로 커버리지가 나오는 6개 모듈. e2e-tests 는 Testcontainers
// 통합 테스트 전용 (Docker 필요) 이라 집계에서 빼고 Kover 자체를 적용하지 않는다.
val koverModules = setOf(
    "security-domain",
    "security-application",
    "security-adapter-in",
    "security-adapter-out",
    "security-streaming",
    "security-bootstrap",
)

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    // 집계 대상 모듈에만 Kover 를 적용해야 루트의 koverHtmlReport / koverXmlReport 가 집계한다.
    if (name in koverModules) {
        apply(plugin = "org.jetbrains.kotlinx.kover")
        // 커버리지는 단위 test 만 — integrationTest (Testcontainers, Docker 필요) 는 Kover 가
        // 강제 실행하지 않도록 instrumentation 대상에서 뺀다. Docker 없이도 koverHtmlReport 가능.
        extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
            currentProject {
                instrumentation {
                    disabledForTestTasks.add("integrationTest")
                }
            }
        }
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
        }
    }

    dependencies {
        // Gradle 8+ 부터 launcher 가 transitively 안 끌려옴 → 명시.
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    // 모든 Test task 공통 — JUnit Platform 사용.
    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // 기본 test task 만 통합 테스트 제외. withType<Test> 로 묶으면 integrationTest 까지
    // excludeTags 가 적용돼 includeTags("integration") 를 덮어써 0 개 테스트가 돌게 된다.
    tasks.named<Test>("test") {
        useJUnitPlatform {
            // 단위 테스트만 실행 — Testcontainers 를 쓰는 통합 테스트는 별도 task 로 격리.
            excludeTags("integration")
        }
    }

    tasks.register<Test>("integrationTest") {
        description = "@Tag(\"integration\") 만 실행 — Testcontainers (Postgres + Kafka + OpenSearch + ClickHouse)"
        group = "verification"
        useJUnitPlatform {
            includeTags("integration")
        }
        shouldRunAfter("test")
        // Testcontainers 가 메모리를 많이 씀 — heap 명시.
        maxHeapSize = "2g"
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
        options.encoding = "UTF-8"
    }
}

// 커버리지 집계 — 루트 koverHtmlReport / koverXmlReport 가 아래 모듈들의 단위 테스트
// 커버리지를 합산한다. e2e-tests 는 Testcontainers 통합 테스트 (별도 task) 라 단위 test
// 에서 커버리지가 0 이므로 집계에서 제외.
dependencies {
    kover(project(":security-domain"))
    kover(project(":security-application"))
    kover(project(":security-adapter-in"))
    kover(project(":security-adapter-out"))
    kover(project(":security-streaming"))
    kover(project(":security-bootstrap"))
}

kover {
    reports {
        filters {
            excludes {
                // Spring Boot main / generated config 은 로직이 없어 커버리지 측정 노이즈.
                classes("com.example.security.SecurityLogSearchApplication")
                classes("com.example.security.SecurityLogSearchApplicationKt")
            }
        }
        // CI 가 줍기 좋은 단일 XML 경로 (코드 커버리지 배지 / 외부 리포터 연동용).
        total {
            xml {
                onCheck.set(false)
            }
            html {
                onCheck.set(false)
            }
        }
    }
}
