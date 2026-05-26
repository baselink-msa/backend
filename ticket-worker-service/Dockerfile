FROM gradle:8.8-jdk17 AS build
WORKDIR /workspace
COPY settings.gradle build.gradle gradlew gradlew.bat ./
COPY gradle ./gradle
COPY ticket-worker-service ./ticket-worker-service
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew :ticket-worker-service:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/ticket-worker-service/build/libs/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
