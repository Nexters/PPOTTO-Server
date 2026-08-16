<!-- Parent: ../AGENTS.md -->

# global.observability

Sentry wiring the Spring Boot starter cannot infer, plus the manual `gen_ai.*` instrumentation for Vertex AI Gemini calls. Everything declarative (DSN, environment, sampling rate, logback bridge levels) lives in `src/main/resources/config/sentry.yml`.

| File | Description |
|------|-------------|
| `SentryUserContextProvider.kt` | `SentryUserProvider` bean that copies the authenticated principal into the Sentry user context as `user.id`. The principal is the raw `UUID` that `BearerTokenAuthenticationFilter` puts into the `SecurityContext`, so no repository lookup happens on the event path. Sentry's own `SentryUserFilter` runs at `Ordered.LOWEST_PRECEDENCE`, i.e. after the Spring Security chain has populated the context |
| `SentryTracesSampler.kt` | `SentryOptions.TracesSamplerCallback` bean that drops `/actuator/**` transactions (health-check noise) by returning `0.0`. Every other request returns `null`, which makes Sentry fall back to the configured `sentry.traces-sample-rate`. Sampling on the request path instead of on transaction name avoids depending on how Sentry names transactions |
| `LlmTelemetry.kt` | `LlmPipeline` enum and `LlmSpanHandle` interface. The abstraction boundary that keeps Sentry types out of call sites. `LlmPipeline` is our own task label (`gen_ai.pipeline.name`), not the Sentry operation name |
| `LlmTracer.kt` | `trace(pipeline, model, attributes) { span -> }` higher-order function. Creates the Sentry AI Client Span (`op = gen_ai.chat`, name = `chat {model}`), sets the required `gen_ai.*` attributes, binds the span as current, records the throwable, and finishes with `OK` / `INTERNAL_ERROR` |
| `GeminiResponseSpans.kt` | `LlmSpanHandle.record(GenerateContentResponse)` extension — maps Gemini `usageMetadata`, `modelVersion`, `responseId`, and candidate finish reasons onto Sentry's token attributes |

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
| `ppotto.llm.photo_count` | request photo count | Custom attribute, `ppotto.llm.` prefixed |

## Rules

- Never put prompts or model output on a span. `gen_ai.input.messages`, `gen_ai.output.messages`, `gen_ai.system_instructions`, and `gen_ai.response.text` carry user photo analysis content and stay unset regardless of org-level scrubbing.
- Call sites keep the `LlmTracer.trace(...) { span -> client 호출.also { span.record(it) } }` one-liner shape. No instrumentation logic in business code.
- `LlmTracer` starts a child span when `Sentry.getSpan()` is non-null and a standalone transaction otherwise. The analysis pipeline runs on `@Async` virtual threads and forks again inside `AnalysisPipelineService`, so the standalone path is the normal one there — never assume a parent exists.
- Attribute keys follow Sentry Conventions; custom keys use the `ppotto.llm.` prefix. Changing a `gen_ai.*` key silently empties the Agents dashboard, so `LlmTracerTest` asserts the literal strings rather than the constants.
- No email or other PII goes into the user context. `sentry.send-default-pii` stays `false`, and enriching the user with an email would mean a per-event cross-domain lookup, which `global` is not allowed to do anyway.
- Returning a non-null value from `SentryTracesSampler` overrides `sentry.traces-sample-rate` entirely. Return `null` for anything that should keep following the configured rate.
- Incoming `sentry-trace` / `baggage` headers are continued by the starter's `SentryTracingFilter`. Do not re-implement trace propagation here.

Update this file when Sentry beans or LLM span attributes change.
