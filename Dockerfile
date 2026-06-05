# syntax=docker/dockerfile:1

# --- Build stage: compile + package the Quarkus fast-jar. Tests run in CI, so skip them here. ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests clean package

# --- Runtime stage: JRE only, non-root. Copy the fast-jar layout least-volatile-first for caching. ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

COPY --from=build /build/target/quarkus-app/lib/ ./lib/
COPY --from=build /build/target/quarkus-app/*.jar ./
COPY --from=build /build/target/quarkus-app/app/ ./app/
COPY --from=build /build/target/quarkus-app/quarkus/ ./quarkus/

# Drop root.
RUN groupadd --system app && useradd --system --gid app app && chown -R app:app /app
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar quarkus-run.jar"]
