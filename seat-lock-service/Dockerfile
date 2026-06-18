FROM gradle:8.8-jdk17 AS build
WORKDIR /workspace
COPY settings.gradle build.gradle gradlew gradlew.bat ./
COPY gradle ./gradle
COPY seat-lock-service ./seat-lock-service
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew :seat-lock-service:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.12.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
COPY --from=build /workspace/seat-lock-service/build/libs/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
