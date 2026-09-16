# Translation Rate Limits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Handle OpenRouter HTTP 429 without rapid retries/model hopping, expose the next retry time, and preserve translation progress.

**Architecture:** Parse only safe rate-limit metadata in the OpenRouter client. A small translation cooldown policy persists a shared deadline and per-job wait information in the workspace; the worker retries the same text group after bounded exponential backoff. Long waits and repeated throttling pause the job with a resume guard. The UI displays the deadline and continues observing cooldown jobs.

**Tech Stack:** Kotlin/Ktor/coroutines, atomic JSON workspace files, Vue/Pinia, generated OpenAPI clients, JUnit and Vitest.

**Spec:** User request of 2026-09-16: handle 429 in code following https://openrouter.ai/docs/api_reference/limits. Existing translation contract: `.ai/specs/2026-09-16-translation-control.md`.

## Global Constraints

- Translation uses only currently free models and the existing OpenRouter key; no paid fallback, new keys, or credit changes.
- Preserve saved EPUB segments, source snapshots, owner scoping, reference context and all device-delivery behavior.
- Never log provider response bodies, book text, API keys or raw exception messages.
- Respect the later of valid `Retry-After` and `X-RateLimit-Reset`; never shorten a server deadline to a local cap.
- Fallback backoff is 60, 120, then 240 seconds plus 0–1000 ms non-negative jitter. Three 429 responses for one text group pause the job; no immediate model fallback on 429.
- Automatically await deadlines up to five minutes; longer deadlines pause without sleeping for hours. Manual Resume before a persisted deadline is rejected before contacting OpenRouter.
- Conservatively share cooldowns among translations using this one configured key, including unknown/provider-scoped limits. This may delay an otherwise available model, but prevents parallel jobs or model changes from bypassing the wait.
- Cooldowns survive process restart. No DB migration or new job-state enum.
- Local frontend tests require `NODE_OPTIONS=--no-experimental-webstorage` on Node 26. No concurrent Gradle runs in this worktree.

### Task 1: Safe rate metadata, durable cooldown and worker retries

**Files:**
- Modify `backend/src/main/kotlin/pl/fairydeck/booksearch/infrastructure/OpenRouterClient.kt` and its test.
- Create `backend/src/main/kotlin/pl/fairydeck/booksearch/infrastructure/OpenRouterRateLimit.kt` and matching test.
- Create `backend/src/main/kotlin/pl/fairydeck/booksearch/service/TranslationRateLimitPolicy.kt` and matching test.
- Modify `service/TranslationService.kt`, `service/TranslationControl.kt`, `service/EpubTranslationWorkspace.kt`, `api/TranslationRoutes.kt` beneath `backend/src/main/kotlin/pl/fairydeck/booksearch/`, with their existing tests.
- Modify `backend/src/main/resources/openapi/api.yaml`, generated frontend API models, and `docs/API.md`.

**Interfaces:**
```kotlin
data class OpenRouterRateLimit(
    val scope: String = "unknown", // platform, provider, unknown
    val retryAt: String? = null,    // ISO-8601 UTC instant
    val limit: Long? = null,
    val remaining: Long? = null
)
// Add val rateLimit: OpenRouterRateLimit? = null to OpenRouterException.
// Add nullable retryAt:String and rateLimitScope:String to TranslationStatus,
// TranslationStatusResponse, and TranslationAttempt. Attempts also expose
// nullable rateLimitLimit:Long and rateLimitRemaining:Long.
```

- [ ] Write failing client/parser regressions: 429 numeric Retry-After, HTTP-date Retry-After, platform reset in epoch seconds and milliseconds, later-of-two hints, malformed/past hints, provider metadata, unknown origin, secret-bearing payload omission. Preserve non-429 HTTP codes and cancellation behavior.
  Example fixed-clock contract:
  ```kotlin
  val now = Instant.parse("2026-09-16T12:00:00Z")
  // Retry-After: 90 and X-RateLimit-Reset: 1789560180000
  // must resolve to 2026-09-16T12:03:00Z, not a shorter local delay.
  assertEquals("2026-09-16T12:03:00Z", parsed.retryAt)
  assertEquals("platform", parsed.scope)
  ```
- [ ] Run focused tests and record the red result; implement a pure parser with an injectable Instant. On HTTP 429, inspect response headers and only whitelisted metadata (`provider_code`/`provider_name` indicate provider, rate-limit headers indicate platform; otherwise unknown). Do not infer daily exhaustion from HTTP status alone. Keep fixed safe error messages.
- [ ] Write policy regressions using an injected clock/wait function and deterministic zero jitter. Verify delays 60/120/240 seconds, a longer server deadline, restart readback, shared-key gating of another job, and expired deadlines. Persist records atomically beneath a dedicated `.rate-limits` directory in the translation workspace; use known job UUIDs for per-job paths, not model IDs as filenames. Preserve the later existing shared deadline when recording another limit.
- [ ] Integrate a bounded retry loop around each individual completion/validation group, not around all previously successful groups. On 429: record safe metadata and retryAt in the attempt, persist cooldown before waiting, retry the same group at most twice, and keep already validated group results in memory. Before any translation completion, enforce the shared gate. Three throttled responses or a deadline beyond five minutes propagate 429 to the chapter loop, which pauses immediately instead of selecting another model. Non-429 validation/transport fallback stays unchanged.
- [ ] Add service tests proving: one 429 then success waits before the second completion; repeated platform/provider/unknown 429 never immediately changes models; a day-long reset pauses after one call; Resume before the deadline sends no completion and is rejected; a newly constructed policy still blocks; after expiry Resume completes; saved previous chapter bytes remain unchanged. Use a fake clock/wait, not real minute-long delays. Tests must inspect actual attempt records and call order/count, not only a mocked policy return.
  ```kotlin
  assertEquals(listOf(60_000L), waits)
  assertEquals("completed", service.status(owner, id).status)
  assertEquals("http_429", service.details(owner, id).attempts.first().errorCode)
  assertNotNull(service.details(owner, id).attempts.first().retryAt)
  ```
- [ ] Expose retryAt/scope in status while the cooldown is active, report the safe current `http_429` reason rather than a stale previous error, and keep structural `resumable` true for paused jobs so the UI can offer a disabled-until-time control. API Resume must independently enforce the deadline; button state is not the security/correctness gate. Existing clients remain compatible through optional fields.
- [ ] Update OpenAPI first and regenerate models using existing repository generation tasks. Document that short waits resume automatically while long/exhausted waits require Resume after the displayed deadline; model changes cannot bypass the shared cooldown.
- [ ] Run focused tests, full backend suite once, and self-review. Commit this task with an English conventional commit and write its report including actual commands/results and limitations.

### Task 2: Visible cooldown controls and final integration validation

**Files:**
- Modify `frontend/src/components/library/TranslationProgress.vue` and its component test.
- Modify `frontend/src/stores/translation.ts` and its store test.
- Add QA evidence under `.ai/qa/` and update `docs/API.md` only where needed.

**Interfaces:** Task 1 optional `retryAt`, `rateLimitScope`, `rateLimitLimit`, `rateLimitRemaining` fields in generated status/attempt models; existing queued/running/paused states remain unchanged.

- [ ] Write failing component tests using fake time: show a human-readable deadline for HTTP 429, distinguish automatic waiting (running) from manual Resume after a long/exhausted wait (paused), disable Resume before retryAt, enable it after time advances, and render rate metadata in attempt history. Timer cleanup must be tested. Model/context editing must not bypass the server gate; export remains available.
  ```ts
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-09-16T12:00:00Z'))
  // Mount paused resumable job with retryAt '2026-09-16T12:01:00Z'.
  expect(wrapper.get('[data-testid="translation-resume-btn"]').attributes('disabled')).toBeDefined()
  await vi.advanceTimersByTimeAsync(61_000)
  expect(wrapper.get('[data-testid="translation-resume-btn"]').attributes('disabled')).toBeUndefined()
  ```
- [ ] Keep polling paused jobs with an active retryAt so the displayed cooldown can expire without reloading. Stop polling once a paused job has no active cooldown; retain existing logout/unmount cleanup and stale-response protections. Add fake-timer store tests for both transitions.
- [ ] Implement Polish guidance: `Limit OpenRouter. Następna próba nie wcześniej niż ...`; running jobs say the retry is automatic, paused jobs say Resume will become available. Avoid claiming every 429 is daily exhaustion. Keep the ordinary model fallback controls and all download/delivery actions unchanged.
- [ ] Run focused component/store tests, then the frontend suite once. Self-review and commit with an English conventional message; report exact results. Controller will exercise a short synthetic 429/Retry-After flow in a real browser and run the final full build after task review.

## Release validation

- [ ] Independent task reviews and final branch review.
- [ ] `./gradlew :backend:test`, frontend tests (Node flag above), `./gradlew build`, `git diff --check`.
- [ ] Synthetic browser check: cooldown visibly waits, no rapid provider calls, progress resumes automatically, long pause rejects premature Resume, chapter export remains available.
- [ ] Publish PR, monitor main CI, verify deployed image revision/health, inspect original paused job without triggering requests before a live cooldown expires. Do not claim the entire book is translated unless status proves it.
