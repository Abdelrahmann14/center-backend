# Build and run the Center API.
#
# Build context is this repository root: `docker build -t center-api .`
# Nothing outside it is needed - the web, mobile and desktop apps live in their
# own repositories and deploy separately.
#
# The Arabic PDF font is bundled in src/main/resources/fonts, so the runtime
# image needs no system fonts installed.

# ── Build ──
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolve in their own layer, so a source-only change does not
# re-download the world.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ── Run ──
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Never run the API as root.
RUN useradd --system --create-home --uid 10001 center
USER center

COPY --from=build --chown=center:center /build/target/center-server-*.jar app.jar

# Containers report the host's memory to the JVM unless told otherwise, so cap
# the heap as a share of the container limit instead.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

# Documentation only: the platform's injected PORT is what actually binds.
EXPOSE 8001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
