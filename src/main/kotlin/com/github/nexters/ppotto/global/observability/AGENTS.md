<!-- Parent: ../AGENTS.md -->

# global.observability

Sentry wiring the Spring Boot starter cannot infer, plus the manual `gen_ai.*` instrumentation for Vertex AI Gemini calls. Everything declarative (DSN, environment, sampling rate, logback bridge levels) lives in `src/main/resources/config/sentry.yml`.

| File | Description |
|------|-------------|
| `SentryUserContextProvider.kt` | `SentryUserProvider` bean that copies the authenticated principal into the Sentry user context as `user.id`. The principal is the raw `UUID` that `BearerTokenAuthenticationFilter` puts into the `SecurityContext`, so no repository lookup happens on the event path. Sentry's own `SentryUserFilter` runs at `Ordered.LOWEST_PRECEDENCE`, i.e. after the Spring Security chain has populated the context |
| `SentryTracesSampler.kt` | `SentryOptions.TracesSamplerCallback` bean that drops `/actuator/**` transactions (health-check noise) by returning `0.0`. Every other request returns `null`, which makes Sentry fall back to the configured `sentry.traces-sample-rate`. Sampling on the request path instead of on transaction name avoids depending on how Sentry names transactions |
| `LlmTelemetry.kt` | `LlmPipeline` enum and `LlmSpanHandle` interface. The abstraction boundary that keeps Sentry types out of call sites. `LlmPipeline` is our own task label (`gen_ai.pipeline.name`), not the Sentry operation name |
| `LlmTracer.kt` | `trace(pipeline, model, attributes) { span -> }` higher-order function. Creates the Sentry AI Client Span (`op = gen_ai.chat`, name = `chat {model}`), sets the required `gen_ai.*` attributes, binds the span as current, records the throwable, and finishes with `OK` / `INTERNAL_ERROR` |
| `GeminiRequestSpans.kt` | `LlmSpanHandle.recordRequest(Content, GenerateContentConfig?)` extension — turns request parts into one `user` message (text parts and `fileData` GCS URIs) and lifts `systemInstruction` out into its own attribute |
| `GeminiResponseSpans.kt` | `LlmSpanHandle.recordResponse(GenerateContentResponse)` extension — maps Gemini `usageMetadata`, `modelVersion`, `responseId`, candidate finish reasons, and candidate content (as `assistant` messages) onto Sentry attributes |

## Sentry AI Agent Monitoring conventions

Spec: [develop.sentry.dev AI Agents Module](https://develop.sentry.dev/sdk/telemetry/traces/modules/ai-agents/), backed by [sentry-conventions](https://github.com/getsentry/sentry-conventions/). The dashboard drops spans that miss the MUST attributes, so these are not free-form.

| Emitted | Value | Why |
|---|---|---|
| span `op` | `gen_ai.chat` | Spec requires `gen_ai.{gen_ai.operation.name}` |
| span name | `chat {model}` | Spec requires `{gen_ai.operation.name} {gen_ai.request.model}` |
| `gen_ai.operation.name` | `chat` | MUST be one of `chat` / `embeddings` / `generate_content` / `text_completion`. Sentry's own `google_genai` integration uses `chat` for `generateContent` |
| `gen_ai.provider.name` | `gcp.gemini` | MUST. Value taken from Sentry's `google_genai` integration |
| `gen_ai.system` | `gcp.gemini` | Deprecated alias of `gen_ai.provider.name`, still what Sentry's own SDK emits. Sent alongside so older Sentry UIs also resolve the provider |
| `gen_ai.request.model` / `gen_ai.response.model` | model id / `modelVersion` | Both MUST |
| `gen_ai.pipeline.name` | `LlmPipeline.value` | Our task label (`photo-classification` etc.). The old OTel code put these in `gen_ai.operation.name`, which Sentry rejects |
| `gen_ai.usage.*` | see below | `input_tokens` = `promptTokenCount + toolUsePromptTokenCount`, `output_tokens` = `candidatesTokenCount + thoughtsTokenCount`, `cache_read.input_tokens` = `cachedContentTokenCount`, `reasoning.output_tokens` = `thoughtsTokenCount`. Cached and reasoning tokens are subsets, matching Sentry's `google_genai` mapping |
| `gen_ai.response.finish_reasons` | comma-joined string | Spec types this as a string, not a list |
| `gen_ai.input.messages` / `gen_ai.output.messages` | `[{"role","parts":[{"type","content"}]}]` JSON string | Current spec shape. Photo parts become `{"type":"uri","modality","mime_type","uri"}` |
| `gen_ai.request.messages` / `gen_ai.response.text` | legacy JSON string shapes | Deprecated in the spec but still what Sentry's own `google_genai` integration emits, so the UI is most likely to render these. Written alongside the current keys |
| `gen_ai.system_instructions` | plain string | Spec types this as a string. No call site sets `systemInstruction` today, so it is normally absent |
| `ppotto.llm.photo_count` | request photo count | Custom attribute, `ppotto.llm.` prefixed |

## Rules

- Prompts, photo GCS URIs, and model output are captured on purpose. This is a deliberate reversal of the earlier no-body policy: the product wants full LLM payloads in Sentry, and the org enforces server-side scrubbing before storage. Do not re-add a blanket body exclusion without checking that decision first.
- Each text part is truncated to 8192 characters before serialization, so the attribute stays parsable JSON. Truncating the serialized JSON instead would produce an unparsable value that the Agents UI drops. Photo parts are GCS URI references, not base64, so they cost almost nothing.
- Call sites keep the `LlmTracer.trace(...) { span -> client 호출.also { span.record(it) } }` one-liner shape. No instrumentation logic in business code.
- `LlmTracer` starts a child span when `Sentry.getSpan()` is non-null and a standalone transaction otherwise. The analysis pipeline runs on `@Async` virtual threads and forks again inside `AnalysisPipelineService`, so the standalone path is the normal one there — never assume a parent exists.
- Attribute keys follow Sentry Conventions; custom keys use the `ppotto.llm.` prefix. Changing a `gen_ai.*` key silently empties the Agents dashboard, so `LlmTracerTest` asserts the literal strings rather than the constants.
- `sentry.send-default-pii: true` makes the SDK additionally send the request `Authorization` header, `Cookie` / `Set-Cookie`, `X-Forwarded-For` / `X-Real-IP` / `Forwarded` / `Remote-Addr`, `X-API-Key`, CSRF/XSRF headers, plus `user.ip_address` and `user.username`. Session cookies (`JSESSIONID`, `SESSIONID`, `SID`, …) are still stripped by the SDK itself. Everything else relies on org-level scrubbing.
- `sentry.max-request-body-size: always` makes `SentrySpringFilter` wrap every request in a `ContentCachingRequestWrapper`, buffering the whole body in memory. No endpoint takes binary uploads (photos go straight to GCS through signed URLs), but `PUT /boards/{id}/layout` carries unbounded freehand `stroke` JSON — that is the endpoint to watch if event payloads or memory become a problem.
- The user context still carries only `user.id` from the SecurityContext principal. Adding an email would mean a per-event cross-domain lookup, which `global` is not allowed to do.
- Returning a non-null value from `SentryTracesSampler` overrides `sentry.traces-sample-rate` entirely. Return `null` for anything that should keep following the configured rate.
- Incoming `sentry-trace` / `baggage` headers are continued by the starter's `SentryTracingFilter`. Do not re-implement trace propagation here.

Update this file when Sentry beans, LLM span attributes, or the data-capture posture change.
