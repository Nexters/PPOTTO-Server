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
RUN --mount=type=bind,from=build,source=/workspace/src/test/resources/dummy-gcs-key.json,target=/tmp/dummy-gcs-key.json,readonly \
    --mount=type=bind,from=build,source=/workspace/src/test/resources/dummy-apple-key.p8,target=/tmp/dummy-apple-key.p8,readonly \
    SPRING_PROFILES_ACTIVE=prod SPRING_FLYWAY_ENABLED=false \
    SERVER_PORT=8080 POSTGRES_HOST=localhost POSTGRES_PORT=5432 POSTGRES_DB=aot \
    POSTGRES_USER=aot POSTGRES_PASSWORD=aot CORS_ALLOWED_ORIGINS=http://localhost \
    SWAGGER_USER=aot SWAGGER_PASSWORD=aot \
    GCS_BUCKET=aot GCS_CREDENTIALS_PATH=/tmp/dummy-gcs-key.json \
    GCS_UPLOAD_SIGNED_URL_EXPIRATION_MINUTES=15 GCS_TIMEOUT_MILLIS=5000 \
    USER_PROVIDER_REFRESH_TOKEN_ENCRYPTION_KEY_BASE64=YW90LWR1bW15LWFlcy1rZXktbm90LWEtcmVhbC1rZXk= \
    REDIS_HOST=localhost REDIS_PORT=6379 REDIS_PASSWORD=aot \
    REDIS_CONNECT_TIMEOUT_MILLIS=2000 REDIS_TIMEOUT_MILLIS=2000 \
    OAUTH_CONNECT_TIMEOUT_MILLIS=3000 OAUTH_READ_TIMEOUT_MILLIS=5000 \
    KAKAO_APP_ID=1 KAKAO_ACCESS_TOKEN_INFO_URI=http://localhost/kakao/access-token-info \
    KAKAO_USER_INFO_URI=http://localhost/kakao/user-info \
    APPLE_CLIENT_ID=aot APPLE_TEAM_ID=aot APPLE_KEY_ID=aot \
    APPLE_PRIVATE_KEY_PATH=/tmp/dummy-apple-key.p8 APPLE_ISSUER=http://localhost \
    APPLE_JWKS_URI=http://localhost/apple/jwks APPLE_TOKEN_URI=http://localhost/apple/token \
    APPLE_REVOKE_URI=http://localhost/apple/revoke \
    APPLE_CLIENT_SECRET_EXPIRATION_DAYS=180 APPLE_JWKS_CACHE_SECONDS=3600 \
    JWT_ISSUER=aot JWT_SECRET=aot-dummy-jwt-secret-not-a-real-key \
    JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=3600 JWT_REFRESH_TOKEN_EXPIRATION_DAYS=30 \
    VERTEX_AI_PROJECT=aot VERTEX_AI_LOCATION=us-central1 \
    VERTEX_AI_CLASSIFY_TIMEOUT_MS=60000 VERTEX_AI_STICKER_TIMEOUT_MS=90000 \
    java -XX:AOTCacheOutput=application.aot -Dspring.context.exit=onRefresh -jar application.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:AOTCache=application.aot", "-jar", "application.jar"]
