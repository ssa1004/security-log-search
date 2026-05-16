// 루트 빌드 — 공통 conventions. 각 모듈이 상속받는 공유 설정.
//
// security-streaming 은 다른 6 모듈과 다르게 Spring Boot 가 아닌 Apache Flink runtime 위에서
// 실행되는 별도 jar 입니다. dependency-management BOM 까지는 공유하되 Spring Boot starter
// 같은 의존성은 각 모듈 build.gradle.kts 에서 명시적으로만 선언합니다.
plugins {
    java
    id("org.springframework.boot") version "3.4.13" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    // domain / application / adapter 가 Kotlin. 적용은 각 모듈 build.gradle.kts 에서만.
    // 버전은 Gradle 8.10 번들 Kotlin 과 정렬.
    kotlin("jvm") version "1.9.24" apply false
    // plugin.spring — @Component / @Controller / @Service 등 Spring 어노테이션 class 를 자동
    //                  open 처리해 CGLIB proxy 가능하게 한다. adapter-in / adapter-out 에 적용.
    kotlin("plugin.spring") version "1.9.24" apply false
    // plugin.jpa — @Entity 가 붙은 class 에 no-arg constructor 합성. adapter-out 만 사용.
    kotlin("plugin.jpa") version "1.9.24" apply false
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

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.13")
        }
    }

    dependencies {
        // 모든 모듈 공통 — Lombok + JUnit launcher.
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
        // Gradle 8+ 부터 launcher 가 transitively 안 끌려옴 → 명시.
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
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
