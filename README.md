# ppotto Server

ppotto 백엔드 서버입니다.

## 기술 스택

| 구분 | 내용 |
|---|---|
| 언어 | Kotlin 2.3.21 |
| 프레임워크 | Spring Boot 4.1.0 |
| JDK | 25 |
| 빌드 | Gradle 9.6.1 (버전 카탈로그 `gradle/libs.versions.toml`) |
| DB | PostgreSQL 18 |
| 마이그레이션 | Flyway |
| 쿼리 | jOOQ (KotlinGenerator) |
| 테스트 | Kotest 6, Testcontainers |

## 사전 요구사항

- Docker (로컬 DB, 테스트, 이미지 빌드에 필요)
- JDK 25 (없으면 Gradle toolchain이 자동으로 내려받음)

## 시작하기

```bash
cp .env.template .env
docker compose up -d
./gradlew bootRun
curl localhost:8080/actuator/health
```

로컬에서 5432 포트를 이미 다른 Postgres가 쓰고 있다면 `.env`의 `POSTGRES_PORT`를 5433 등으로 변경합니다.

## 설정 구조

- `application.yml`은 애플리케이션 이름, 기본 프로파일, `spring.config.import` 목록만 가집니다.
- 실제 설정은 `src/main/resources/config/` 아래 관심사별 파일로 분리되어 있습니다.
- 프로파일 분기는 각 파일 안에서 `spring.config.activate.on-profile` 멀티 문서로 처리합니다.
- 프로파일은 `local`(기본), `prod` 두 가지입니다.
- 파일 간 키 중복은 금지합니다. import 목록에서 뒤에 오는 파일이 우선하므로 중복 시 의도치 않은 override가 발생합니다.
- 값은 `${VAR:default}` 형태의 환경 변수 placeholder만 사용합니다. 로컬에서는 spring-dotenv가 `.env`를 읽고, prod에서는 실제 환경 변수를 사용합니다.

## DB 워크플로

1. `src/main/resources/db/migration/`에 `V{n}__{설명}.sql` 형식으로 마이그레이션을 작성합니다.
2. `./gradlew flywayMigrate jooqCodegen`을 실행합니다.
3. `src/generated/jooq/`에 생성된 코드를 커밋합니다.

생성 코드를 커밋하므로 CI와 Docker 빌드는 DB 없이 동작합니다. 앱 기동 시에는 Flyway가 자동으로 마이그레이션을 적용합니다.

## 테스트

```bash
./gradlew test
```

Testcontainers가 Postgres 컨테이너를 직접 띄우므로 Docker만 있으면 됩니다. Kotest 스펙은 JUnit Platform 위에서 실행되며, Spring 연동은 `ProjectConfig`의 `SpringExtension` 전역 등록으로 처리됩니다.

## Docker 이미지

```bash
docker build -t ppotto-server .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e POSTGRES_HOST=<host> -e POSTGRES_DB=<db> \
  -e POSTGRES_USER=<user> -e POSTGRES_PASSWORD=<password> \
  ppotto-server
```

- 멀티스테이지 빌드로 Spring Boot 레이어(`dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`)를 분리해 캐시 효율을 높였습니다.
- 런타임은 non-root `spring` 사용자로 동작합니다.
- prod 프로파일은 DB 환경 변수가 없으면 기동에 실패합니다. 의도된 fail-fast 동작입니다.
