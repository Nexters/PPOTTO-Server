<!-- Parent: ../AGENTS.md -->

# global.openapi

Swagger 문서의 반복 응답 계약과, 타입 안전한 예시를 코드로 주입하는 기반을 제공합니다.

| File | Description |
|------|-------------|
| `ApiErrorResponses.kt` | 400(`COMMON-001`), 409(`COMMON-006`) 공통 오류 응답과 `data`가 항상 null인 200 성공 응답 합성 어노테이션. 응답 코드·설명·스키마만 선언하고 예시는 붙이지 않습니다 |
| `ApiErrorResponse.kt` | 문서 전용 실패 응답 봉투 스키마(`success`, `data`, `error`). 실제 실패 응답은 `GlobalExceptionHandler`가 `ApiResponse.error`로 만듭니다 |
| `ApiExample.kt` | 예시 한 건(`ApiExample`: 이름, summary, 실제 DTO 인스턴스)과 operation 단위 묶음(`OperationExamples`: 요청 예시, 응답 코드별 예시) |
| `ApiExampleProvider.kt` | 도메인이 구현하는 기여 인터페이스. `KFunction`(API 인터페이스 메서드 참조) → `OperationExamples` 맵을 노출합니다 |
| `ApiExampleRegistry.kt` | 모든 `ApiExampleProvider`를 모아 `KFunction.javaMethod` 기준 맵으로 만들고, `HandlerMethod`를 `ClassUtils.getInterfaceMethodIfPossible`로 API 인터페이스 메서드까지 되짚어 조회합니다. 같은 함수에 예시가 중복 등록되면 조용히 덮어쓰지 않고 기동 시점에 즉시 실패합니다 |
| `ApiExampleFactory.kt` | 애플리케이션 `ObjectMapper` 빈으로 예시 객체를 직렬화한 뒤 swagger 모델의 `Example`로 감쌉니다. 이름을 준 예시는 swagger-core와 같은 방식으로 `description`에도 이름을 채웁니다 |
| `ApiExampleOperationCustomizer.kt` | springdoc `OperationCustomizer`. 레지스트리에서 찾은 예시를 requestBody와 응답 코드별 media type에 주입합니다 |
| `ApiExamples.kt` | 도메인에 종속되지 않는 예시: 빈 성공 응답, `COMMON-001`(필드 오류 포함), `COMMON-004`, `COMMON-006`. 실패 예시를 짧게 만드는 `error`/`errorExample` 헬퍼도 여기 있습니다 |

## Rules

- 엔드포인트 요약과 도메인별 설명은 각 도메인의 `presentation/XxxApi.kt` 인터페이스에 둡니다. controller에는 Swagger 어노테이션을 두지 않습니다.
- 예시는 `@ExampleObject` JSON 문자열이 아니라 실제 요청·응답 DTO 인스턴스로 정의합니다. DTO가 바뀌면 컴파일 에러가 나고, 직렬화는 운영 `ObjectMapper`가 하므로 `default-property-inclusion: non_null`처럼 운영 설정이 그대로 반영됩니다(null 필드는 예시에서도 빠집니다).
- 도메인 예시는 그 도메인의 `presentation/XxxApiExamples.kt`에 `@Component ... : ApiExampleProvider`로 둡니다. 여러 도메인이 같은 의미로 쓰는 예시만 이 패키지의 `ApiExamples`에 둡니다.
- 매핑 키는 `operationId`가 아니라 API 인터페이스 메서드 참조(`AuthApi::login`)입니다. springdoc의 `operationId`는 메서드 이름이 겹치면 `create_1`처럼 순서에 따라 붙어 안정적이지 않습니다.
- 필드 단위 예시는 `@Schema(example = ...)`를 우선 사용합니다. `ApiExampleProvider`는 봉투 단위 예시(요청 바디 전체, 응답 전체)에만 씁니다.
- 성공 응답에는 `@ApiResponse(useReturnTypeSchema = true)`만 지정하고 `@Content`를 적지 않습니다. 예시는 커스터마이저가 생성된 media type에 나중에 넣으므로 반환 타입 스키마 `$ref`가 그대로 남습니다.
- 401 응답은 `OpenApiConfig.operationCustomizer`가 `ApiExamples.UNAUTHORIZED`로 일괄 주입하므로 개별 API에 다시 선언하지 않습니다.
- 새 operation을 추가하면 `ApiExampleProvider`에도 반드시 등록합니다. 누락은 `OpenApiExampleWiringTest`가 잡습니다.

Update this file when shared OpenAPI annotations or the example injection mechanism changes.
