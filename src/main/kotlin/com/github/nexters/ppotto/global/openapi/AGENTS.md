<!-- Parent: ../AGENTS.md -->

# global.openapi

Swagger 문서의 반복 응답 계약을 제공하는 공통 어노테이션입니다.

| File | Description |
|------|-------------|
| `ApiErrorResponses.kt` | 400, 404, 409 공통 오류 응답 설명 어노테이션 |

## Rules

- 엔드포인트 요약과 도메인별 설명은 각 presentation controller에 둡니다.
- 여러 도메인에서 같은 의미로 쓰는 오류 응답만 이 패키지에 둡니다.

Update this file when shared OpenAPI annotations change.
