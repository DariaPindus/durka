# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS base
WORKDIR /workspace

FROM base AS dev
# Source is bind-mounted by docker-compose for live iteration; nothing is COPY'd here.
ENV GRADLE_USER_HOME=/home/gradle/.gradle
ENTRYPOINT ["./gradlew"]
CMD ["bootRun", "--continuous"]

FROM base AS build
COPY . .
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# Pre-create and own /data/tdlib before switching users: a fresh named volume mounted here
# is owned by root by default, but Docker copies this directory's ownership from the image
# into the volume the first time it's populated - appuser needs write access from the start,
# since it never runs as root to fix this itself (see backend/README.md for the fuller story).
RUN useradd --system --uid 10001 appuser && mkdir -p /data/tdlib && chown -R appuser:appuser /data/tdlib
COPY --from=build /workspace/build/libs/*.jar app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
