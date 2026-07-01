FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew
ARG MODULE=ssutoday-api
RUN ./gradlew :${MODULE}:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
ARG MODULE=ssutoday-api
COPY --from=build /workspace/${MODULE}/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
