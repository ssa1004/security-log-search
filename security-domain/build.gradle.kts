// 도메인 모듈 — 외부 의존성 0. 순수 Kotlin (JVM 21) + Java 호환.
//
// 도메인 모델은 Kotlin 으로 작성하되, 호출 측 6 모듈은 Java 그대로다. Java record 와 동일한
// 바이너리 형태를 유지하려고 value object 는 @JvmRecord data class 로 옮겼다 — 호출 측의
// record-style accessor (`tenantId()`, `value()` 등) 가 그대로 컴파일된다.
plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    // Domain 은 외부 framework 의존성 금지. JSR-310 (java.time) + Kotlin stdlib 만 사용.
    // Lombok 은 root build.gradle.kts 에서 공통 적용 (Kotlin 코드에는 미적용).

    testImplementation(platform("org.junit:junit-bom:5.10.5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.assertj:assertj-core:3.27.7")
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
