<!-- Parent: ../AGENTS.md -->

# observability

LLM 호출 계측 래퍼. OTel javaagent가 못 잡는 GenAI 속성(모델명·토큰수·finishReason)을 OTel GenAI semantic convention(`gen_ai.*`)으로 스팬에 붙인다.

| File | Description |
|------|-------------|
| `LlmTelemetry.kt` | `LlmOperation` enum과 `LlmSpanHandle` 인터페이스. 호출부에 OTel API 타입이 새지 않게 하는 추상화 경계 |
| `LlmTracer.kt` | `trace(operation, model, attributes) { span -> }` 고차함수. 스팬 생성·컨텍스트 바인딩·예외 기록·종료를 전담 |
| `GeminiResponseSpans.kt` | `LlmSpanHandle.record(GenerateContentResponse)` 확장 — usageMetadata 토큰수·modelVersion·finishReason을 스팬 속성으로 자동 매핑 |

## Rules

- 호출부는 `LlmTracer.trace(...) { span -> client 호출.also { span.record(it) } }` 한 줄 형태를 유지한다. 비즈니스 로직에 계측 코드를 섞지 않는다.
- 스팬 속성 키는 OTel GenAI semantic convention을 따르고, 커스텀 속성은 `ppotto.llm.` 접두사를 쓴다.
- javaagent 없이 실행되면 `GlobalOpenTelemetry`가 noop이므로 오버헤드 없이 무시된다. 래퍼는 agent 유무와 무관하게 안전해야 한다.
