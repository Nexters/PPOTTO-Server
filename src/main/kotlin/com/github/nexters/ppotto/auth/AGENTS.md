<!-- Parent: ../AGENTS.md -->

# auth

소셜 로그인과 서비스 토큰 인증 도메인입니다.

| Directory | Description |
|-----------|-------------|
| `domain/` | provider, 로그인 command/result, 토큰 모델, AUTH error code |
| `application/` | 체이닝된 로그인/활성 사용자 재검증/재발급/로그아웃 orchestration과 user/terms 연결 port |
| `infrastructure/oauth/` | Fluent Kakao API, Apple identity token/JWKS/code exchange/revoke adapter |
| `infrastructure/integration/` | Fluent user 가입·활성 상태·기본 보드·약관·세션 연결과 provider 계정 해지 adapter |
| `infrastructure/token/` | Fluent HS256 access JWT와 opaque refresh token 발급, Redis rotation adapter |
| `infrastructure/security/` | Fluent Bearer 인증 context 조립, UUID principal, 401/403 ApiResponse writer |
| `presentation/AuthApi.kt` | `/auth/login`, `/auth/refresh`, `/auth/logout` version 1 mapping and Swagger contract |
| `presentation/AuthController.kt` | Fluent Auth API implementation with request binding and required UUID logout user injection |
| `presentation/dto/` | Swagger-described authentication request and response schemas |
| `config/` | 검증된 Kakao, Apple, OAuth HTTP timeout, JWT properties와 adapter/application bean wiring. 생성자 프로퍼티 그룹 사이 한 줄 개행 유지 |

## Rules

- provider SDK나 HTTP 응답 타입을 application/domain에 노출하지 않습니다.
- user/terms/board Repository를 직접 참조하지 않고 `application.port`만 사용합니다.
- auth와 user의 provider enum, auth와 terms의 응답 dto는 adapter에서 명시적으로 변환합니다.
- refresh token 재발급은 token issue/rotation 전에 user application service로 활성 사용자를 재검증합니다.
- provider refresh token 평문은 user port 경계까지만 전달하며 user 저장 adapter가 즉시 암호화합니다.
- refresh token은 원문을 Redis key/value에 저장하지 않고 SHA-256 hash로만 식별합니다.
- OAuth RestClient는 설정된 connect/read timeout을 반드시 적용합니다.

Update this file when auth packages or contracts change.
