<!-- Parent: ../AGENTS.md -->

# auth

소셜 로그인과 서비스 토큰 인증 도메인입니다.

| Directory | Description |
|-----------|-------------|
| `domain/` | provider, 로그인 command/result, 가입 트랜잭션 결과(`AuthSignup`), 토큰 모델, AUTH error code |
| `application/` | 표현식 기반 로그인/활성 사용자 재검증/재발급/로그아웃 체인과 user/terms 연결 port. `@Qualifier(SIGNUP_TRANSACTION)`으로 주입한 `signupTransaction`(`TransactionOperations`)으로 가입 구간만 트랜잭션으로 감쌉니다 |
| `infrastructure/oauth/` | Fluent Kakao API, Apple identity token/JWKS/code exchange/revoke adapter |
| `infrastructure/integration/` | Fluent user 가입·활성 상태·기본 보드·약관·세션 연결과 provider 계정 해지 adapter. 가입 adapter는 `@Transactional`로 사용자와 기본 보드를 한 단위로 묶습니다 |
| `infrastructure/token/` | Fluent HS256 access JWT와 opaque refresh token 발급, Redis rotation adapter |
| `infrastructure/security/` | Expression-bodied Bearer 인증 context 조립과 예외 정규화, UUID principal, 401/403 ApiResponse writer |
| `presentation/AuthApi.kt` | `/auth/login`, `/auth/refresh`, `/auth/logout` version 1 mapping and Swagger contract: 응답 코드, 설명, 스키마만 선언하고 예시는 `AuthApiExamples`가 주입합니다 |
| `presentation/AuthController.kt` | Fluent Auth API implementation with request binding and required UUID logout user injection |
| `presentation/AuthApiExamples.kt` | `ApiExampleProvider` 구현. provider별 로그인 요청, 신규 가입/재로그인 응답, `AUTH-001`~`AUTH-004` 실패 예시를 실제 DTO 인스턴스로 정의합니다 |
| `presentation/dto/` | Swagger-described authentication request and response schemas |
| `config/` | 검증된 Kakao, Apple, OAuth HTTP timeout, JWT properties와 adapter/application bean wiring. `AuthTransactionConfig`는 가입 전용 `signupTransaction` bean(`TransactionOperations`, timeout `SIGNUP_TRANSACTION_TIMEOUT_SECONDS` = 5초)을 정의합니다. 생성자 프로퍼티 그룹 사이 한 줄 개행 유지 |

## Rules

- provider SDK나 HTTP 응답 타입을 application/domain에 노출하지 않습니다.
- user/terms/board Repository를 직접 참조하지 않고 `application.port`만 사용합니다.
- 신규 가입은 사용자 생성, 기본 보드 생성, 미동의 약관 조회를 한 트랜잭션으로 처리합니다. 중간 단계가 실패하면 users 행까지 롤백해 보드 없는 유령 계정을 남기지 않습니다.
- Apple authorization code 교환 실패 검사는 반드시 `signupTransaction.execute` 안에서 예외로 던집니다. 이 예외를 밖으로 옮기거나 안에서 catch하면 유령 Apple 계정이 커밋되므로, `AuthSignupRollbackIntegrationTest`의 애플 교환 실패 시나리오가 실제 트랜잭션 경계로 이를 막습니다.
- 가입 트랜잭션은 Spring 기본 공유 `transactionTemplate`이 아니라 `AuthTransactionConfig`의 전용 bean을 씁니다. timeout이 없으면 `uk_users_provider_uid` unique index 대기와 `pg_advisory_xact_lock` 대기가 무제한으로 커넥션 풀을 점유합니다. 주입은 이름 기반 fallback에 기대지 않고 `@Qualifier(SIGNUP_TRANSACTION)`으로 고정합니다.
- 가입 adapter는 호출자의 트랜잭션에 기대지 않고 스스로 `@Transactional` 경계를 가집니다. port를 단독으로 호출해도 사용자와 기본 보드는 함께 커밋되거나 함께 롤백됩니다.
- provider HTTP 호출과 token 발급/Redis 저장은 트랜잭션 밖에서 실행합니다. `login`에 `@Transactional`을 붙이면 provider read timeout 동안 DB connection이 묶이므로 금지합니다.
- 같은 소셜 계정의 동시 가입은 `uk_users_provider_uid` 부분 unique index와 `saveIfAbsent` 후 재조회로 직렬화합니다. 정확히 한 요청만 신규 가입이 되고 기본 보드도 한 번만 생성됩니다.
- auth와 user의 provider enum, auth와 terms의 응답 dto는 adapter에서 명시적으로 변환합니다.
- refresh token 재발급은 token issue/rotation 전에 user application service로 활성 사용자를 재검증합니다.
- provider refresh token 평문은 user port 경계까지만 전달하며 user 저장 adapter가 즉시 암호화합니다.
- refresh token은 원문을 Redis key/value에 저장하지 않고 SHA-256 hash로만 식별합니다.
- OAuth RestClient는 설정된 connect/read timeout을 반드시 적용합니다.

Update this file when auth packages or contracts change.
