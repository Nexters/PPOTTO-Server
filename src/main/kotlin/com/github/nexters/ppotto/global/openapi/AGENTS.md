<!-- Parent: ../AGENTS.md -->

# global.openapi

Swagger 문서의 반복 응답 계약과 도메인에 속하지 않는 공통 예시를 제공합니다.

| File | Description |
|------|-------------|
| `ApiErrorResponses.kt` | 400(`COMMON-001`), 409(`COMMON-006`) 공통 오류 응답과 `data`가 항상 null인 200 성공 응답 합성 어노테이션. 모두 `ApiErrorResponse` 스키마와 `@ExampleObject` 예시를 함께 붙입니다 |
| `ApiErrorResponse.kt` | 문서 전용 실패 응답 봉투 스키마(`success`, `data`, `error`). 실제 실패 응답은 `GlobalExceptionHandler`가 `ApiResponse.error`로 만듭니다 |
| `ApiExamples.kt` | 도메인에 종속되지 않는 예시 JSON 상수: 빈 성공 응답, `COMMON-001`(필드 오류 포함), `COMMON-004`, `COMMON-006` |

## Rules

- 엔드포인트 요약과 도메인별 설명은 각 도메인의 `presentation/XxxApi.kt` 인터페이스에 둡니다. controller에는 Swagger 어노테이션을 두지 않습니다.
- 여러 도메인에서 같은 의미로 쓰는 오류 응답과 예시만 이 패키지에 둡니다. 도메인 고유 에러 코드 예시는 해당 도메인의 `presentation/dto/XxxApiExamples.kt`에 둡니다.
- 예시 JSON은 `@ExampleObject(value = ...)`에 넣어야 하므로 `const val` 원시 문자열이어야 합니다. `trimIndent()`는 컴파일 타임 상수가 아니라 쓸 수 없습니다.
- 필드 단위 예시는 `@Schema(example = ...)`를 우선 사용합니다. `@ExampleObject`는 로그인 provider별 요청처럼 상황별 예시가 여러 개 필요한 곳에만 씁니다.
- 성공 응답에 예시를 붙일 때는 `@ApiResponse(useReturnTypeSchema = true)`를 함께 지정하고 `@Content`에 `mediaType`을 적지 않습니다. 둘 중 하나라도 빠지면 반환 타입 스키마가 사라지거나 media type이 둘로 나뉩니다.
- 401 응답은 `OpenApiConfig.operationCustomizer`가 `ApiExamples.UNAUTHORIZED`로 일괄 주입하므로 개별 API에 다시 선언하지 않습니다.
- 새 `@ExampleObject` 상수를 추가하면 `OpenApiExampleContractTest`에 등록해 strict 역직렬화 검증을 받게 합니다.

Update this file when shared OpenAPI annotations or examples change.
