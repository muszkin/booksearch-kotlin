# Translation control QA

Local real Ktor backend + SQLite, actual built Vue frontend, agent-browser
v0.34.0 / Chrome 153. Synthetic EPUBs and a controlled local OpenRouter HTTP
server (no production credentials or external model completions).

Observed end-to-end:

- Selected a downloaded Polish reference by the same author and entered a glossary.
- Explicitly confirmed processing and started translation.
- First chapter completed; second received the wrong number of JSON items.
- Three router attempts followed by three attempts using another free model;
  the chapter paused, preserving the first chapter.
- Details showed requested/actual models, item counts, safe validation reason,
  token usage, finish reason and all seven HTTP attempts.
- Clicked Markdown export while paused. Downloaded `rozdzial-1.md` contained
  the heading and `Cześć świecie!`, not source text.
- Selected the second model manually, resumed after restoring the fake provider,
  and reached completed 2/2 with a separate Polish library entry.
- SHA-256 of the saved first chapter before and after resume was identical:
  `9c1c6783123a7cf169ba679951fb1199dc118da5491307d433a3e72f39b89eb3`.
- Reload retained the completed job. Browser reported no JavaScript errors.
- Final artifact: made the administrator default unavailable, selected a valid
  free model in the start form, and obtained a fresh successful estimate. Selected
  a Polish reference and saw its chapter numbers and actual sample before
  confirmation; start remained gated by consent and preview readiness.

Final validation: backend 359 tests, zero failures/errors; frontend 353 tests
across 77 files; `./gradlew build` successful. Frontend tests ran with
`NODE_OPTIONS=--no-experimental-webstorage` because the local Node 26 experimental
global interferes with jsdom's storage; production builds use Node 22. No test was
skipped. Additional frontend warnings are existing scrollTo/deprecation notices.

Synthetic local screenshots: `/tmp/translation-browser-VlMWWf/start.png` and
`/tmp/translation-browser-VlMWWf/diagnostics.png`. These are local artifacts,
not a claim that production or literary translation quality has been verified.

Production recovery target is the existing paused job for Inhibitor Phase.
Completed checkpoint files were backed up locally before deployment; no
production state was changed during these local tests.
