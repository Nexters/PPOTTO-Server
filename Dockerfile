FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY buildSrc/ buildSrc/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies || true
COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon clean bootJar -x test

FROM bellsoft/liberica-openjre-debian:25-cds AS extractor
WORKDIR /builder
COPY --from=build /workspace/build/libs/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM bellsoft/liberica-openjre-debian:25-cds
WORKDIR /application
RUN groupadd --system --gid 1001 spring && useradd --system --uid 1001 --gid spring spring
COPY --from=extractor --chown=spring:spring /builder/extracted/dependencies/ ./
COPY --from=extractor --chown=spring:spring /builder/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=spring:spring /builder/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=spring:spring /builder/extracted/application/ ./
RUN SPRING_PROFILES_ACTIVE=prod SPRING_FLYWAY_ENABLED=false \
    SERVER_PORT=8080 POSTGRES_HOST=localhost POSTGRES_PORT=5432 POSTGRES_DB=aot \
    POSTGRES_USER=aot POSTGRES_PASSWORD=aot CORS_ALLOWED_ORIGINS=http://localhost \
    SWAGGER_USER=aot SWAGGER_PASSWORD=aot \
    java -XX:AOTCacheOutput=application.aot -Dspring.context.exit=onRefresh -jar application.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:AOTCache=application.aot", "-jar", "application.jar"]
