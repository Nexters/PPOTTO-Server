# 뽀또 Server

## 시작하기

```bash
cp .env.template .env
docker compose up -d
./gradlew bootRun
```

- API 문서: `http://localhost:8080/swagger-ui.html`
- 헬스체크: `http://localhost:8080/actuator/health`

## 프로젝트 구조

```
Server/
├── build.gradle.kts             빌드 스크립트 (버전은 gradle/libs.versions.toml에서 관리)
├── buildSrc/                    Flyway + jOOQ codegen 빌드 플러그인
├── compose.yaml                 로컬 PostgreSQL + pgvector (기본 포트 54782)
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

AI 에이전트용 상세 규칙은 각 폴더의 `AGENTS.md`에 있습니다. 폴더의 코드를 수정하면 해당 `AGENTS.md`도 함께 갱신합니다.
