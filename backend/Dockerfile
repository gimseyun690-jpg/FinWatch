# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace/backend

COPY backend/gradlew backend/gradlew.bat backend/settings.gradle backend/build.gradle ./
COPY backend/gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY backend/src ./src
RUN ./gradlew bootJar --no-daemon -ParchiveFileName=finwatch-api.jar

FROM eclipse-temurin:21-jre-alpine-3.24 AS runtime
ARG APP_VERSION=dev
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="FinWatch API" \
      org.opencontainers.image.version="$APP_VERSION" \
      org.opencontainers.image.revision="$VCS_REF" \
      org.opencontainers.image.source="https://github.com/gimseyun690-jpg/FinWatch"

RUN apk upgrade --no-cache \
    && apk add --no-cache ca-certificates curl \
    && addgroup -S -g 10001 finwatch \
    && adduser -S -D -H -u 10001 -G finwatch finwatch

WORKDIR /app
COPY --from=build --chown=finwatch:finwatch /workspace/backend/build/libs/finwatch-api.jar /app/finwatch-api.jar

USER 10001:10001
EXPOSE 8080
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=4 \
  CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/finwatch-api.jar"]
