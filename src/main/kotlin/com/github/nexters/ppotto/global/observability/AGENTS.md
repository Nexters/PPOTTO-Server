<!-- Parent: ../AGENTS.md -->

# global.observability

Sentry wiring the Spring Boot starter cannot infer, the manual `gen_ai.*` instrumentation for Vertex AI Gemini calls, and the HTTP payload capture that puts request/response headers and bodies on the transaction span. Everything declarative (DSN, environment, sampling rate, logback bridge levels) lives in `src/main/resources/config/sentry.yml`.

| File | Description |
|------|-------------|
| `SentryUserContextProvider.kt` | `SentryUserProvider` bean that copies the authenticated principal into the Sentry user context as `user.id`. The principal is the raw `UUID` that `BearerTokenAuthenticationFilter` puts into the `SecurityContext`, so no repository lookup happens on the event path. Sentry's own `SentryUserFilter` runs at `Ordered.LOWEST_PRECEDENCE`, i.e. after the Spring Security chain has populated the context |
| `SentryTracesSampler.kt` | `SentryOptions.TracesSamplerCallback` bean that drops `/actuator/**` transactions (health-check noise) by returning `0.0`. Every other request returns `null`, which makes Sentry fall back to the configured `sentry.traces-sample-rate`. Sampling on the request path instead of on transaction name avoids depending on how Sentry names transactions |
| `LlmTelemetry.kt` | `LlmPipeline` enum and `LlmSpanHandle` interface. The abstraction boundary that keeps Sentry types out of call sites. `LlmPipeline` is our own task label (`gen_ai.pipeline.name`), not the Sentry operation name |
| `LlmTracer.kt` | `trace(pipeline, model, attributes) { span -> }` higher-order function. Creates the Sentry AI Client Span (`op = gen_ai.chat`, name = `chat {model}`), sets the required `gen_ai.*` attributes, binds the span as current, records the throwable, and finishes with `OK` / `INTERNAL_ERROR` |
| `GeminiRequestSpans.kt` | `LlmSpanHandle.recordRequest(Content, GenerateContentConfig?)` extension — turns request parts into one `user` message (text parts and `fileData` GCS URIs) and lifts `systemInstruction` out into its own attribute |
| `SentryHttpPayloadFilter.kt` | Servlet filter at `Ordered.HIGHEST_PRECEDENCE + 2` that copies request/response headers and bodies onto the active transaction span. Runs after Sentry's own filters (`sentrySpringFilter` = `MIN_VALUE`, `sentryTracingFilter` = `MIN_VALUE + 1`) so `Sentry.getSpan()` is the bound transaction. Reuses the `ContentCachingRequestWrapper` that `SentrySpringFilter` already created instead of double-buffering |
| `HttpPayloadAttributes.kt` | Pure attribute builder for the filter — key naming, header masking, JSON body redaction, truncation. Has no Sentry or servlet dependency so it is unit-testable on its own |
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
| `gen_ai.request.messages` / `gen_ai.response.text` / `gen_ai.system` | **not sent** | sentry-conventions marks all three Deprecated and names the current key to use instead. They were emitted alongside the current keys for a while out of uncertainty about which the UI reads; that duplication is gone |
| `gen_ai.system_instructions` | plain string | Spec types this as a string. No call site sets `systemInstruction` today, so it is normally absent |
| `ppotto.llm.photo_count` | request photo count | Custom attribute, `ppotto.llm.` prefixed |

## HTTP payload capture on transactions

`sentry.max-request-body-size` only reaches **error events**: Sentry's `RequestBodyExtractingEventProcessor` implements `process(SentryEvent, Hint)` and never the `SentryTransaction` overload, so transactions carry no body. `SentryHttpPayloadFilter` fills that gap by writing directly onto the transaction span.

Keys come from [sentry-conventions](https://github.com/getsentry/sentry-conventions/tree/main/model/attributes/http), not guesswork:

| Key | Type | Convention status |
|---|---|---|
| `http.request.header.<lowercase-name>` | **scalar string** | Documented as `string[]`, but we send a scalar on purpose — see below |
| `http.response.header.<lowercase-name>` | **scalar string** | Same |
| `http.request.body.data` | string | Documented, `apply_scrubbing: auto` |
| `http.response.body.data` | string | No documented counterpart; named symmetrically with the request key |
| `http.request.body.size` / `http.response.body.size` | int | `http.response.body.size` is documented; the request one is symmetric. Always the pre-truncation byte count |

`http.request.method` and `http.response.status_code` are deliberately not set here — Sentry's own tracing filter already puts them on the transaction.

## Header values are scalars, not arrays — do not "fix" this

sentry-conventions types `http.request.header.<key>` / `http.response.header.<key>` as `string[]`, but **Relay/EAP rejects array span attributes**. Sending lists produced this on the real dev ingest:

```
sentry._meta.fields.attributes.http.response.header.content-type:
  {"meta":{"":{"err":[["invalid_data",{"reason":"expected scalar attribute"}]]}}}
```

Multi-value headers are therefore joined with `", "`, which is the combining rule HTTP itself defines for repeated field lines (RFC 9110 §5.3). `Set-Cookie` is the one header that must not be comma-joined, and it is masked to `[Filtered]` before joining ever matters. This was confirmed fixed in production — the `invalid_data` meta entries are gone and headers now store as scalars.

Reverting this to arrays to "match the convention" re-breaks ingestion. `SentryHttpPayloadEndToEndTest` asserts every attribute value is a scalar against a real Tomcat.

## There is no span attribute count limit — verify with all 40 ClickHouse buckets

**Do not trim span attributes believing there is a cap. There isn't one.**

This cost two rounds of pointless work. A ClickHouse query suggested "no span has more than 13 attributes", with common headers (`host`, `user-agent`, `content-type`) and every body key apparently missing while obscure ones survived. Both HTTP payload capture and half the `gen_ai` attributes were removed to fit that phantom budget, then restored.

The truth: `eap_items_1_local` stores attributes hash-distributed across **`attributes_string_0` … `attributes_string_39`, 40 bucket columns**. The query only read `_0`–`_3`. The "13 limit" was just what happened to land in four buckets, and the "random" survivors were hash placement. Reading all 40 buckets shows 30+ attributes per span, with bodies and headers stored correctly all along.

This matches the code: Relay annotates `SpanData` `trim = false` and its `other` map `retain = true`, and there is no attribute-count constant anywhere in its normalization path.

So when checking whether an attribute reached storage, **query all 40 `attributes_string_*` buckets**. A partial-bucket query is indistinguishable from data loss.

## Rules

- Prompts, photo GCS URIs, and model output are captured on purpose. This is a deliberate reversal of the earlier no-body policy: the product wants full LLM payloads in Sentry, and the org enforces server-side scrubbing before storage. Do not re-add a blanket body exclusion without checking that decision first.
- Each text part is truncated to 8192 characters before serialization, so the attribute stays parsable JSON. Truncating the serialized JSON instead would produce an unparsable value that the Agents UI drops. Photo parts are GCS URI references, not base64, so they cost almost nothing.
- Call sites keep the `LlmTracer.trace(...) { span -> client 호출.also { span.record(it) } }` one-liner shape. No instrumentation logic in business code.
- `LlmTracer` starts a child span when `Sentry.getSpan()` is non-null and a standalone transaction otherwise. The analysis pipeline runs on `@Async` virtual threads and forks again inside `AnalysisPipelineService`, so the standalone path is the normal one there — never assume a parent exists.
- Attribute keys follow Sentry Conventions; custom keys use the `ppotto.llm.` prefix. Changing a `gen_ai.*` key silently empties the Agents dashboard, so `LlmTracerTest` asserts the literal strings rather than the constants.
- `sentry.send-default-pii: true` makes the SDK additionally send the request `Authorization` header, `Cookie` / `Set-Cookie`, `X-Forwarded-For` / `X-Real-IP` / `Forwarded` / `Remote-Addr`, `X-API-Key`, CSRF/XSRF headers, plus `user.ip_address` and `user.username`. Session cookies (`JSESSIONID`, `SESSIONID`, `SID`, …) are still stripped by the SDK itself. Everything else relies on org-level scrubbing.
- **Do not rely on org-level scrubbing for span attributes.** Relay's `sensitive_fields` rule is applied to the generic `$string || $number || $array || $object` selector, and `Pii::Maybe` fields are documented as "only stripped when addressed with a specific path selector, but generic selectors such as `$string` do not apply". `TraceContext.data` — where attributes set on a transaction root span land — is annotated `pii = "maybe"`. On top of that, a JSON body is a single opaque string to Relay, so a `RedactPair` rule keyed on `accessToken` can never reach inside it. `HttpPayloadAttributes` therefore masks in code, and that is the only guarantee.
- Header masking covers `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`, `X-API-Key` plus any header name containing a sensitive fragment. Body redaction walks the parsed JSON and replaces values whose key contains one, which is what keeps the real JWTs out of the `POST /auth/login` response. Numbers and booleans are left alone so `accessTokenExpiresIn: 3600` survives while `accessToken` does not.
- Only JSON bodies are captured. Anything else records size alone, which keeps binary and streaming payloads out and avoids regex-guessing at unparsable formats.
- Bodies are truncated to 16384 characters with a `…[truncated]` marker, and the untruncated byte count stays in the `*.body.size` attribute. `PUT /boards/{id}/layout` carries unbounded freehand `stroke` JSON, so this cap is what stops a single drawing-heavy save from dominating the payload.
- `ContentCachingResponseWrapper` buffers the whole response, so the filter skips `/actuator/**` and the swagger/OpenAPI paths, which are the only large or non-JSON responses this service serves. It also skips entirely when there is no active span and never copies the body on an async dispatch.
- `copyBodyToResponse()` must run in a `finally`, after reading `contentAsByteArray`. Each attribute group is applied under its own `runCatching`, so one failing group cannot suppress the others, and bodies are written **before** headers so the highest-value data is never the first thing lost to a downstream limit.
- **The payload attributes are known to leave the SDK correctly — do not "fix" this in application code again.** Running the real image against a local envelope sink showed `http.request.body.data`, `http.response.body.data`, and both `*.body.size` values present and masked on the wire, while dev ClickHouse showed them missing. Three rounds were spent changing app code for what the wire capture proves is an ingest-side loss. Re-verify with an envelope sink before touching the filter.
- A request body the application never read (401 from the security chain, 404 on an unmapped path) is still captured: the filter drains the input stream afterwards when `Content-Length` is known and within the cache limit. Verified against a real servlet container, not MockMvc — `SentryHttpPayloadFilterTest` alone cannot see container semantics.
- `sentry.max-request-body-size: always` makes `SentrySpringFilter` wrap JSON requests in a `ContentCachingRequestWrapper`, buffering the body in memory. The payload filter reuses that wrapper rather than adding a second one; when it has to create its own it bounds the cache at 64 KiB.
- The user context still carries only `user.id` from the SecurityContext principal. Adding an email would mean a per-event cross-domain lookup, which `global` is not allowed to do.
- Returning a non-null value from `SentryTracesSampler` overrides `sentry.traces-sample-rate` entirely. Return `null` for anything that should keep following the configured rate.
- Incoming `sentry-trace` / `baggage` headers are continued by the starter's `SentryTracingFilter`. Do not re-implement trace propagation here.

## Profiling

`io.sentry:sentry-async-profiler` registers an async-profiler backed `IContinuousProfiler` through the `io.sentry.profiling.JavaContinuousProfilerProvider` service loader. Only **continuous** profiling exists for the JVM — the artifact ships no transaction-based profiler — so `profile-session-sample-rate` is what enables it and `profiles-sample-rate` must stay unset (`SentryOptions.isContinuousProfilingEnabled()` returns false the moment the legacy rate or sampler is present).

`profiling-traces-dir-path` is mandatory and the directory must already exist and be writable; the SDK does not create it and silently logs `Disabling profiling because ...` otherwise. The image creates `/application/profiling` owned by `spring` for this.

The profiler starts async-profiler with `event=wall`, which samples via timers rather than perf events. Verified in the real runtime image (`bellsoft/liberica-openjre-debian:25-cds`): the native library loads and produces a populated JFR while running **non-root under `security_opt: no-new-privileges:true`, with no `SYS_ADMIN` and no `perf_event_paranoid` change**, and alongside the `-XX:AOTCache` entrypoint. Do not relax those container settings for profiling — they were checked and are not needed. `--enable-native-access=ALL-UNNAMED` is on the entrypoint because JDK 25 warns on `System::load` and a future JDK will block it outright.

## Sentry Logs

`sentry.logs.enabled: true` turns on the Logs product; `sentry.logging.minimum-level: info` is what makes the logback appender forward records to it. These are separate from `minimum-event-level` / `minimum-breadcrumb-level`, which drive error events and breadcrumbs and have nothing to do with the Logs view. INFO and above is sent unsampled in every environment by product decision.

Update this file when Sentry beans, LLM span attributes, or the data-capture posture change.
