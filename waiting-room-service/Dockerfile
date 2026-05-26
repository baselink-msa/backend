FROM gradle:8.8-jdk17 AS build
WORKDIR /workspace
COPY settings.gradle build.gradle gradlew gradlew.bat ./
COPY gradle ./gradle
COPY waiting-room-service ./waiting-room-service
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew :waiting-room-service:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/waiting-room-service/build/libs/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
