// 도메인 모듈 — 외부 의존성 0. 순수 Java 21 + Lombok 만.
plugins {
    `java-library`
}

dependencies {
    // Domain 은 외부 framework 의존성 금지. JSR-310 (java.time) + Java 21 record / sealed 만 사용.
    // Lombok 은 root build.gradle.kts 에서 공통 적용.
}
