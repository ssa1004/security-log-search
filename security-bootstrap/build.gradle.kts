// Bootstrap — Spring Boot main + application.yml + Flyway 마이그레이션.
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":security-application"))
    implementation(project(":security-adapter-in"))
    implementation(project(":security-adapter-out"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
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
