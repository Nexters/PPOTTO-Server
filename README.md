# 뽀또 Server

## 시작하기

### 로컬 개발

```bash
cp .env.template .env
docker compose up -d
./gradlew bootRun
```

- API 문서: `http://localhost:8080/swagger-ui.html`
- 헬스체크: `http://localhost:8080/actuator/health`

### Dev 서버

Dev 서버는 Caddy, API, PostgreSQL 18 + pgvector를 함께 실행한다. API와 DB 포트는
외부에 공개하지 않고 Caddy의 80/443 포트만 공개한다.

```bash
cp .env.template .env.dev
mkdir -p ../secrets
# ../secrets/gcs-dev-service-account.json 배치
docker compose -f compose.deploy.yaml -f compose.dev.yaml config
docker compose -f compose.deploy.yaml -f compose.dev.yaml up -d --build
```

`.env.dev`에서 `APP_DOMAIN`, DB 및 Swagger 비밀번호, `GCS_BUCKET`을 실제 Dev 환경
값으로 변경한다. 앱은 서버 배포 설정을 사용하기 위해 `SPRING_PROFILES_ACTIVE=prod`로
실행된다. CORS는 `compose.dev.yaml`이 `CORS_ALLOWED_ORIGINS=*`로 고정해 모든 origin을
허용하므로 `.env.dev`에서 따로 설정하지 않아도 된다.

Sentry는 `.env.dev`의 `SENTRY_DSN`만 채우면 켜진다. `SENTRY_ENVIRONMENT=dev`와
트레이싱 샘플링 비율 `1.0`은 `compose.dev.yaml`이 고정하므로 환경파일에 넣지 않는다.
`SENTRY_DSN`을 비워 두면 SDK가 비활성 상태로 뜨고 이벤트를 보내지 않는다.

### Production 서버

Production은 Dev와 프로젝트명, 환경파일, GCS 자격증명, Docker 볼륨이 모두 분리된다.

```bash
cp .env.template .env.production
mkdir -p ../secrets
# ../secrets/gcs-production-service-account.json 배치
docker compose -f compose.deploy.yaml -f compose.production.yaml config
docker compose -f compose.deploy.yaml -f compose.production.yaml up -d --build
```

Sentry는 `.env.production`의 `SENTRY_DSN`만 채우면 켜진다. `SENTRY_ENVIRONMENT=production`과
트레이싱 샘플링 비율 `0.1`은 `compose.production.yaml`이 고정한다.

두 Compose는 모두 80/443 포트를 사용하므로 같은 호스트에서 동시에 실행하지 않는다.
Production과 Dev를 동시에 운영해야 할 때는 서버를 분리하거나 공용 프록시 구성을 사용한다.

### CD 설정

`dev` 또는 `main` push의 CI가 성공하면 CD 워크플로가 해당 커밋을 이미지로 빌드해
Container Registry에 커밋 SHA 태그로 푸시한다. 이후 서버에 SSH로 접속해 검증된 커밋만
fast-forward하고 같은 SHA의 이미지를 pull해 실행한다. `dev`는 GitHub Environment
`development`, `main`은 `production`을 사용한다. 두 환경의 배포 절차는 하나의 공통
job을 사용하고, 브랜치별 설정만 먼저 선택한다.

각 GitHub Environment에 다음 Secret을 등록한다.

| Secret | 설명 |
|---|---|
| `DEPLOY_HOST` | 배포 서버 공인 IP 또는 호스트명 |
| `DEPLOY_PORT` | SSH 포트. 비어 있으면 30438 |
| `DEPLOY_USER` | 배포 계정 |
| `DEPLOY_SSH_KEY` | 배포 계정에 접속할 OpenSSH private key |
| `DEPLOY_KNOWN_HOSTS` | `호스트 ssh-ed25519 공개키` 형식의 고정된 SSH 호스트 키 |
| `REGISTRY_HOST` | Registry 호스트명. 예: `ghcr.io` |
| `REGISTRY_REPOSITORY` | Registry 내 이미지 경로. 예: `nexters/ppotto-server` |
| `REGISTRY_USERNAME` | Registry 로그인 계정 |
| `REGISTRY_PASSWORD` | 이미지를 push할 수 있는 Registry token 또는 비밀번호 |

Production Environment에는 GitHub의 required reviewer를 설정해 운영 배포 전에 승인을
요구하는 것을 권장한다. 서버 작업 트리에 수정 사항이 있거나 fast-forward가 불가능하면
배포는 중단되며, 강제 reset은 수행하지 않는다. Private Registry라면 배포 전에 서버에서도
`docker login REGISTRY_HOST`를 한 번 실행해 pull 권한을 저장해야 한다.

GCS 서비스 계정 JSON은 저장소 밖 `/home/ppotto/secrets`에 보관하며 디렉터리는 `700`,
파일은 `600` 권한을 사용한다.

## 프로젝트 구조

```
Server/
├── build.gradle.kts             빌드 스크립트 (버전은 gradle/libs.versions.toml에서 관리)
├── buildSrc/                    Flyway + jOOQ codegen 빌드 플러그인
├── compose.yaml                 로컬 PostgreSQL + pgvector (기본 포트 54782)
├── compose.deploy.yaml          서버 배포 공통 Caddy + API + PostgreSQL
├── compose.dev.yaml             Dev 서버 환경별 override
├── compose.production.yaml      Production 서버 환경별 override
├── Caddyfile                    HTTPS 및 API reverse proxy
├── Dockerfile                   멀티스테이지 + 레이어 분리 + non-root 실행
└── src/
    ├── main/kotlin/com/github/nexters/ppotto/
    │   ├── PpottoApplication.kt
    │   ├── global/              공통 모듈
    │   │   ├── config/          시큐리티, CORS, Swagger 설정
    │   │   ├── error/           에러 코드, 예외 계층, 전역 예외 핸들러
    │   │   ├── logging/         요청 로깅 (requestId MDC)
    │   │   └── response/        ApiResponse, PageResponse
    │   └── {domain}/            도메인 패키지 (아래 구조로 추가)
    │       ├── presentation/    Controller, 요청/응답 dto
    │       ├── application/     Service, QueryService
    │       ├── domain/          도메인 모델, 에러 코드
    │       └── infrastructure/  Repository (jOOQ)
    ├── main/resources/
    │   ├── application.yml      프로파일 기본값과 config import 목록만
    │   ├── config/*.yml         관심사별 설정 분리 (datasource, security, cors 등)
    │   └── db/migration/        Flyway 마이그레이션 (V{yyyyMMddHHmmss}__{설명}.sql)
    ├── generated/jooq/          jOOQ 생성 코드 (커밋 대상, 직접 수정 금지)
    └── test/kotlin/             Kotest BehaviorSpec + Testcontainers
```

- 도메인 의존 방향은 presentation → application → domain ← infrastructure이고, domain은 프레임워크에 의존하지 않습니다.
- 도메인 간 참조는 상대 도메인의 Service를 통해서만 합니다.
- 프로파일은 `local`(기본), `prod` 두 가지입니다. 설정 placeholder에는 기본값을 두지 않으며, 기본값은 `.env.template`이 유일한 소스입니다.

## 도메인 예시

`photo` 도메인을 만든다면 다음 구조가 됩니다.

```
photo/
├── presentation/
│   ├── PhotoController.kt           엔드포인트. request dto를 받아 Service 호출, ApiResponse로 응답
│   └── dto/
│       ├── PhotoCreateRequest.kt    요청 dto. @Valid 검증 어노테이션 위치
│       └── PhotoResponse.kt         응답 dto. jOOQ 생성 타입을 그대로 노출하지 않음
├── application/
│   ├── PhotoService.kt              변경 유스케이스. @Transactional 경계, 도메인 모델 생성과 검증
│   └── PhotoQueryService.kt         조회 전용. jOOQ 프로젝션으로 dto를 직접 조립
├── domain/
│   ├── Photo.kt                     도메인 모델. 상태와 규칙을 가진 순수 Kotlin 클래스
│   └── PhotoErrorCode.kt            PHOTO-001 형식, ErrorCode 인터페이스 구현
└── infrastructure/
    └── PhotoRepository.kt           DSLContext 기반 저장/조회. DB 접근은 여기서만
```

흐름은 다음과 같습니다.

```kotlin
@RestController
@RequestMapping("/api/photos")
class PhotoController(
    private val photoService: PhotoService,
) {
    @PostMapping
    fun create(
        @Valid @RequestBody request: PhotoCreateRequest,
    ): ApiResponse<PhotoResponse> = ApiResponse.success(photoService.create(request))
}
```

```kotlin
@Service
class PhotoService(
    private val photoRepository: PhotoRepository,
) {
    @Transactional
    fun create(request: PhotoCreateRequest): PhotoResponse {
        val photo = Photo.create(request.title, request.imageUrl)
        return PhotoResponse.from(photoRepository.save(photo))
    }
}
```

```kotlin
enum class PhotoErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "PHOTO-001", "사진을 찾을 수 없습니다."),
}
```

존재하지 않는 사진 조회처럼 실패가 필요한 지점에서는 `throw NotFoundException(PhotoErrorCode.PHOTO_NOT_FOUND)`처럼 던지면 전역 핸들러가 응답으로 변환합니다.

## 규칙

- 브랜치: `dev` 기준으로 `feat/이슈번호-기능간단설명` 형식(예: `feat/1-user-board-image-entity`)으로 만들고, PR은 `main`이 아니라 `dev`로 보냅니다.
- 커밋 메시지: `$operator($domain): $message` 형식, 한글로 작성합니다. operator는 `feat` `fix` `refactor` `chore` `test` `docs` `style` `ci`.
- 코드 스타일은 ktlint와 detekt가 강제합니다. 커밋 전 `./gradlew build`가 통과해야 합니다.
- API 응답은 `ApiResponse` envelope로 감싸고, 에러 코드는 `도메인-번호` 형식(`COMMON-001`)을 씁니다.
- DB 스키마 변경은 마이그레이션 작성 → `./gradlew flywayMigrate jooqCodegen` → 생성 코드 커밋 순서로 합니다.
- main 브랜치는 PR로만 머지되며(squash), CI가 빌드·테스트·린트를 검증합니다.

## 문서

API와 DB 설계는 스펙 드리븐 개발 방식으로 `docs/`에서 먼저 정의하고 구현합니다. `docs/`는 항상 정본이므로, 구현 중 설계가 바뀌면 같은 변경 단위에서 문서도 갱신합니다.

- API 계약 문서: `docs/api-spec/api-spec.md`
- ERD 문서: `docs/erd/README.md`, `docs/erd/schema.dbml`

AI 에이전트용 상세 규칙은 각 폴더의 `AGENTS.md`에 있습니다. 폴더의 코드를 수정하면 해당 `AGENTS.md`도 함께 갱신합니다.
