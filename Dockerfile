FROM eclipse-temurin:8-jdk AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
COPY libs libs
RUN chmod +x gradlew && ./gradlew --no-daemon downloadDependencies
COPY src src
COPY docs/openapi.yaml docs/openapi.yaml
RUN ./gradlew --no-daemon clean build

FROM eclipse-temurin:8-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/build/libs/regulus-api.jar /app/regulus-api.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/regulus-api.jar"]
