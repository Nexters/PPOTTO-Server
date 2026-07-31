<!-- Parent: ../AGENTS.md -->

# global.security

HTTP 인증 사용자를 컨트롤러 인자로 주입하는 공통 MVC 경계입니다.

| File | Description |
|------|-------------|
| `AuthenticatedUser.kt` | UUID 인증 사용자를 요구하는 컨트롤러 인자 어노테이션 |
| `CurrentUser.kt` | 공개 API에서 선택적으로 UUID 인증 사용자를 받는 인자 어노테이션 |
| `CurrentUserArgumentResolver.kt` | SecurityContext UUID principal을 fluent 분기로 해석하고 필수 인증 누락을 `COMMON-004`로 변환 |

## Rules

- 인증 principal은 UUID만 허용하며 다른 타입은 선택 인증에서도 `COMMON-004`로 거부합니다.
- 필수 인증은 `@AuthenticatedUser`, 공개 API의 선택 인증은 `@CurrentUser`를 사용합니다.
- 리소스 소유권과 도메인 권한은 application service에서 검증합니다.

Update this file when authentication argument contracts change.
