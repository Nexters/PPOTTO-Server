<!-- Parent: ../AGENTS.md -->

# auth

소셜 로그인과 서비스 토큰 인증 도메인입니다.

| Directory | Description |
|-----------|-------------|
| `domain/` | provider, 로그인 command/result, 토큰 모델, AUTH error code |
| `application/` | 로그인/재발급/로그아웃 orchestration과 user/terms 연결 port |
| `infrastructure/oauth/` | Boot HTTP Service Client 기반 Kakao API, Apple identity token/JWKS/code exchange/revoke adapter |
| `infrastructure/token/` | HS256 access JWT와 opaque refresh token 발급, Redis rotation adapter |
| `infrastructure/security/` | Bearer filter, UUID principal, 401/403 ApiResponse writer |
| `presentation/` | `/auth/login`, `/auth/refresh`, `/auth/logout` version 1 API |
| `config/` | Validated Kakao, Apple, JWT properties와 HTTP Service Client/application bean wiring |

## Rules

- provider SDK나 HTTP 응답 타입을 application/domain에 노출하지 않습니다.
- user/terms/board Repository를 직접 참조하지 않고 `application.port`만 사용합니다.
- provider refresh token 평문은 user port 경계까지만 전달하며 user 저장 adapter가 즉시 암호화합니다.
- refresh token은 원문을 Redis key/value에 저장하지 않고 SHA-256 hash로만 식별합니다.
- OAuth HTTP Service Client group은 설정된 connect/read timeout을 반드시 적용합니다.
- Application, presentation, and infrastructure flows prefer scope-function chains and collection transforms over mutable accumulators.

Update this file when auth packages or contracts change.
