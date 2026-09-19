FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY pom.xml ./

RUN mvn \
    --batch-mode \
    --no-transfer-progress \
    dependency:go-offline

COPY src ./src

RUN mvn \
    --batch-mode \
    --no-transfer-progress \
    -DskipTests \
    package


FROM eclipse-temurin:21-jre-alpine

ENV TZ=UTC

RUN addgroup -S finance \
    && adduser -S -G finance finance

WORKDIR /app

COPY --from=build \
    --chown=finance:finance \
    /workspace/target/finance-api-*.jar \
    /app/app.jar

USER finance

EXPOSE 8080

HEALTHCHECK \
    --interval=30s \
    --timeout=5s \
    --start-period=30s \
    --retries=3 \
    CMD wget --quiet --output-document=- \
        "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health/readiness" \
        || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]