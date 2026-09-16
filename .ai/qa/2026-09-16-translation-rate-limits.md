# Translation rate-limit QA

Code under test: `bd819fe` (plus this evidence-only commit). Real built Ktor/Vue
application, isolated SQLite and synthetic two-chapter EPUB, local controlled
OpenRouter HTTP fixture. Chrome 153 / agent-browser v0.34.0. No production keys,
book contents or external model completions used for this QA.

## Exercised flows

- Selected `fixture/free` in the start form, confirmed processing and started.
- First chapter completed. Second received HTTP 429 with Retry-After 2 seconds,
  platform limit 20, remaining 0. The configured minimum backoff took precedence.
- UI showed running 1/2, an explicit UTC deadline and automatic-retry guidance.
  Attempt history showed scope, limit, remaining zero and deadline.
- Provider calls: 200 at 13:31:14.650Z, 429 at 13:31:14.678Z, then 200 at
  13:32:14.849Z (60.171 seconds after 429). No intervening completion calls or
  model switching. UI reached completed 2/2 without manual Resume.
- During cooldown, downloaded first chapter Markdown contained
  `# Rozdział 1` and `Cześć świecie!`.
- Started another synthetic job with Retry-After 3600 seconds. It paused at 1/2,
  with retryAt 14:32:44.566436351Z and disabled Resume. UI explained that manual
  Resume becomes available after the deadline; mobile width 390px was inspected.
- Direct authenticated Resume with a changed model returned HTTP 409,
  `Translation rate limit cooldown is still active`. Provider call count remained
  two (first chapter 200, second 429); model change did not bypass the gate.
- Downloaded the saved first chapter while paused; byte-identical to the earlier
  export. Browser page-error list was empty.

Synthetic screenshots and exports are local artifacts in
`/tmp/translation-browser-VlMWWf/`: `rate-wait.png`, `rate-paused-mobile.png`,
`rate-paused-mobile-summary.png`, `rate-chapter-1.md`, `rate-paused-chapter.md`.
They are not evidence of production literary translation quality.

## Validation and review

Final gates on bd819fe, in repository-required order:

- `./gradlew :backend:test`: 377 tests, zero failures/errors/skips.
- `NODE_OPTIONS=--no-experimental-webstorage pnpm --dir frontend test -- --run`:
  368 tests / 77 files passed. The flag handles local Node 26; production uses 22.
- `./gradlew build`: passed.
- Independent task reviews and final branch review passed. Final scoped re-review
  confirmed the submillisecond-deadline correction and its deterministic test.
- Existing JVM sharing, Node deprecation, jsdom scrollTo and dynamic-import
  warnings remain baseline limitations, not failures introduced by this change.

Self-QA exception: these exercised-flow results support `qa-self-verified` and
`qa-approved` under SDLC.md; they are not a separate human/GitHub approval.
