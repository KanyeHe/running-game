# syntax=docker/dockerfile:1

# Build H5 and its shared TypeScript modules from source.
FROM node:24-alpine AS frontend
WORKDIR /workspace
COPY web/package.json web/package-lock.json ./web/
RUN --mount=type=cache,target=/root/.npm cd web && npm ci --ignore-scripts --no-audit --no-fund
COPY web/ ./web/
COPY client/tsconfig.core.json ./client/
COPY client/assets/scripts/model.ts client/assets/scripts/Selection.ts client/assets/scripts/Transport.ts ./client/assets/scripts/
RUN node web/scripts/build.cjs \
    && node web/node_modules/typescript/bin/tsc -p client/tsconfig.core.json \
    && cd web && npm test

# Compile and test the application on JDK 25.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY server/pom.xml ./pom.xml
COPY server/.mvn/settings.xml ./settings.xml
COPY server/src/ ./src/
COPY --from=frontend /workspace/server/src/main/resources/static/ ./src/main/resources/static/
COPY deploy/HealthCheck.java ./health/HealthCheck.java
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -s settings.xml verify \
    && javac --release 25 -d health health/HealthCheck.java

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/target/paodekuai-0.1.0.jar ./app.jar
COPY --from=build /workspace/health/HealthCheck.class ./health/HealthCheck.class
EXPOSE 8080
USER 10001:10001
HEALTHCHECK --interval=20s --timeout=10s --start-period=60s --retries=5 \
    CMD ["java", "-Xms16m", "-Xmx32m", "-cp", "/app/health", "HealthCheck"]
ENTRYPOINT ["java","-jar","app.jar"]
