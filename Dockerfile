# syntax=docker/dockerfile:1

# Build stage
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -f core/pom.xml install -DskipTests
RUN --mount=type=cache,target=/root/.m2 mvn -q -f storage/file/pom.xml install -DskipTests
RUN --mount=type=cache,target=/root/.m2 mvn -q -f storage/bigquery/pom.xml install -DskipTests
RUN --mount=type=cache,target=/root/.m2 mvn -q -f storage/gcs/pom.xml install -DskipTests
RUN --mount=type=cache,target=/root/.m2 mvn -q -f reference/otp/pom.xml package -DskipTests

# Runtime stage: distroless Java image with non-root user and no shell.
# Only the built jar is copied over.
FROM gcr.io/distroless/java25-debian13
COPY --from=build /src/reference/otp/target/trakpi.jar /app/trakpi.jar
USER nonroot
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/trakpi.jar"]
