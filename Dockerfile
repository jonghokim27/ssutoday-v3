FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
ARG MODULE=ssutoday-api
RUN ./gradlew :${MODULE}:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
ARG MODULE=ssutoday-api
ARG PROFILE=default
ENV PROFILE=${PROFILE}
COPY --from=build /workspace/${MODULE}/build/libs/*.jar app.jar
ENTRYPOINT ["sh", "-c", "exec java -Dspring.profiles.active=${PROFILE} -jar /app/app.jar"]
