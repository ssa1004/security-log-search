# --- build stage ---
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Gradle wrapper + 빌드 스크립트만 먼저 복사 — 의존성 캐시 hit 향상
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY security-domain/build.gradle.kts security-domain/
COPY security-application/build.gradle.kts security-application/
COPY security-adapter-in/build.gradle.kts security-adapter-in/
COPY security-adapter-out/build.gradle.kts security-adapter-out/
COPY security-streaming/build.gradle.kts security-streaming/
COPY security-bootstrap/build.gradle.kts security-bootstrap/
COPY e2e-tests/build.gradle.kts e2e-tests/
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 복사 + 빌드 (Spring Boot 측만)
COPY security-domain security-domain
COPY security-application security-application
COPY security-adapter-in security-adapter-in
COPY security-adapter-out security-adapter-out
COPY security-bootstrap security-bootstrap
RUN ./gradlew --no-daemon :security-bootstrap:bootJar -x test

# --- runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 보안 — non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /workspace/security-bootstrap/build/libs/security-bootstrap-*-boot.jar app.jar

# Container-aware JVM + virtual threads (Loom)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
