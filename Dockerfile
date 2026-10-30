# syntax=docker/dockerfile:1

# Build stage
# Liberica 25.0.5.0.3 plus Maven from the Apache tarball: the maven:3.9 images
# (Temurin, Corretto, SapMachine) still ship a JDK that trusts the compromised
# eMudhra CAs, and Maven fetches every dependency through that trust store.
FROM bellsoft/liberica-openjdk-debian:25.0.5.0.3-5 AS build
ARG MAVEN_VERSION=3.9.16
ARG MAVEN_SHA512=831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6
RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -o /tmp/maven.tar.gz \
 && printf '%s  /tmp/maven.tar.gz\n' "${MAVEN_SHA512}" > /tmp/maven.sha512 \
 && sha512sum -c /tmp/maven.sha512 \
 && mkdir -p /opt/maven \
 && tar -xzC /opt/maven --strip-components=1 -f /tmp/maven.tar.gz \
 && rm /tmp/maven.tar.gz /tmp/maven.sha512
ENV PATH="/opt/maven/bin:${PATH}"
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -f core/pom.xml install -DskipTests && mvn -q -f storage/file/pom.xml install -DskipTests && mvn -q -f storage/bigquery/pom.xml install -DskipTests && mvn -q -f storage/gcs/pom.xml install -DskipTests && mvn -q -f reference/otp/pom.xml package -DskipTests

# Runtime stage: distroless Java image with non-root user and no shell.
# Only the built jar is copied over.
FROM gcr.io/distroless/java25-debian13:nonroot
COPY --from=build /src/reference/otp/target/trakpi.jar /app/trakpi.jar
USER nonroot
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/trakpi.jar"]
