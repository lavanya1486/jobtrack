# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build stage
# Dependencies are resolved in their own layer so a source-only change does not
# re-download the world.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# Tests run in CI (mvn verify); the image only needs the artifact.
RUN mvn -B -ntp clean package -DskipTests \
    && cp target/jobtrack-backend.jar /workspace/app.jar

# ------------------------------------------------------------- runtime stage
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl is used by the container healthcheck against /actuator/health.
RUN apk add --no-cache curl \
    && addgroup -S -g 1001 jobtrack \
    && adduser -S -u 1001 -G jobtrack jobtrack

WORKDIR /app
COPY --from=build --chown=jobtrack:jobtrack /workspace/app.jar app.jar

USER jobtrack
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# `sh -c` so JAVA_OPTS is expanded; `exec` so the JVM receives SIGTERM (graceful shutdown).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
