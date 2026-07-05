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
RUN useradd --system --uid 10001 appuser
COPY --from=build /workspace/build/libs/*.jar app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
