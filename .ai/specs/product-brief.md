# BookSearch — tłumaczenie EPUB na polski — product brief

- Date: 2026-09-12 · Mode: existing + own · Owner: muszkin
- Coverage: 58 claims — 55 sourced (interview 0, data 0, document 50, product 5, benchmark 0), 0 synthetic, 3 assumed; 1 entry on the collection plan
- Definition of Ready signed by: muszkin; the first translation quality test is accepted untested by D21
- Sources: `README.md`; `docs/ARCHITECTURE.md`; `BACKWARD_COMPATIBILITY.md`; `.ai/specs/research/decisions/D01-private-personal-use.md` through `D23-job-estimate-content.md`; [OpenRouter Models API](https://openrouter.ai/docs/api/api-reference/models/get-models), checked 2026-09-12

## Vision

Muszkin translates a downloaded English EPUB into a separate Polish EPUB for private reading in the library and on an e-reader. `[DOCUMENT]` `D01`, `D02`

## Target group and stakeholders

- Customer, user, owner, and scope decider: muszkin, private user of this self-hosted instance. `[DOCUMENT]` `D01`
- Administrator: configures the default eligible OpenRouter model. `[DOCUMENT]` `D05`, `D11`

## Problems, with evidence

- BookSearch searches, downloads, converts, and delivers books, but the documented feature set has no translation workflow. `[PRODUCT]` `README.md`
- Muszkin needs a Polish EPUB with controlled terminology across a book and selected author context. `[DOCUMENT]` `D02`, `D03`

## Product and how it stands out

- The product adds resumable chapter translation through OpenRouter, using only models with a current listed price of zero; the administrator may modify the configured model selection. `[DOCUMENT]` `D04`, `D05`, `D16`, `D22`
- Context is a persisted glossary plus one to five chapters sampled once from selected Polish EPUB references by the same author. `[DOCUMENT]` `D19`, `D13`
- A user can find and download a compatible Polish EPUB through the existing search flow, manually confirm the result, and use it as context while retaining ordinary library and delivery behavior. `[DOCUMENT]` `D18`, `D15`

## Goals and success criteria

- Product goal: by 2026-09-30 validate a roughly 300-page English EPUB translated into Polish with no lost chapters and 20 selected fragments judged useful by muszkin. `[DOCUMENT]` `D07`, `D17`
- User outcome: muszkin can obtain, download, and deliver a separate Polish EPUB after a chapter-based translation completes. `[DOCUMENT]` `D02`, `D04`
- Primary metric, baseline today, threshold, date: translation workflow baseline is absent; one complete target EPUB and 20 useful selected fragments by 2026-09-30. `[PRODUCT]` `README.md`; `[DOCUMENT]` `D17`
- What must not get worse: contextual-reference status never limits Kindle or PocketBook delivery. `[DOCUMENT]` `D14`, `D18`

## Scope

- **Now:** from a downloaded English EPUB, a user confirms external processing and an effort estimate, starts a chapter-based translation with the default currently free model, optionally chooses compatible Polish author context, resumes failed work, and receives a separate Polish EPUB that can be downloaded or delivered. `[DOCUMENT]` `D02`, `D04`, `D16`, `D20`, `D22`, `D23`
- **Now (2026-09-16 extension):** per-job free model selection, automatic free-model fallback, diagnostics and chapter TXT/Markdown export. `[USER]` D24
- **Later:** automatic series detection and a richer translation editing experience. `[DOCUMENT]` `D08`
- **Not doing:** see Non-goals.

## Domain glossary

| Term | Meaning | Owned by | Visible to |
|---|---|---|---|
| Source EPUB | Downloaded English EPUB selected for translation. `[DOCUMENT]` `D02` | muszkin | user |
| Translated EPUB | Separate Polish EPUB produced from a source EPUB. `[DOCUMENT]` `D02` | muszkin | user |
| Contextual reference | A selected Polish EPUB by the same author, used for glossary and chapter context. `[DOCUMENT]` `D18`, `D19` | muszkin | user |
| Glossary | Persisted terminology derived from selected contextual references. `[DOCUMENT]` `D03`, `D19` | muszkin | user |
| Eligible model | OpenRouter model with current zero price; administrator configuration may modify the selection. `[DOCUMENT]` `D05`, `D22` | administrator | administrator |
| Translation job | Resumable sequence of chapter translation attempts. `[DOCUMENT]` `D04`, `D16` | muszkin | user |

## Key flows

- Current state: a user searches, downloads, converts, and delivers a library book without a translation step. `[PRODUCT]` `README.md`; `docs/ARCHITECTURE.md`
- Future state: a user selects a source EPUB, confirms the external-processing notice and estimate, optionally adds compatible context, follows per-chapter progress, manually resumes a stopped chapter if necessary, then accesses the new Polish EPUB. `[DOCUMENT]` `D02`, `D04`, `D16`, `D20`

## Business rules

| Id | Rule | Applies to | Source | Owner | Status | Review by | Required path to change |
|---|---|---|---|---|---|---|---|
| R01 | Only a downloaded EPUB may be translation input. | translation start | `[DOCUMENT]` `D02` | muszkin | active | 2026-09-30 | Superseding decision record by muszkin |
| R02 | Translation output is a separate Polish EPUB linked to its source and remains downloadable and deliverable. | completed translation | `[DOCUMENT]` `D02` | muszkin | active | 2026-09-30 | Superseding decision record by muszkin |
| R03 | A contextual reference must be a Polish EPUB by the same author, and the user confirms a searched result before it is downloaded. | context acquisition | `[DOCUMENT]` `D18`, `D15` | muszkin | active | 2026-09-30 | Superseding decision record by muszkin |
| R04 | The user selects one to five chapters; sampling happens once, is shown before use, and is persisted. | contextual reference | `[DOCUMENT]` `D13` | muszkin | active | 2026-09-30 | Superseding decision record by muszkin |
| R05 | The default model is administrator-configured and eligible only when its current listed price is zero; licence is not an eligibility criterion. | model selection | `[DOCUMENT]` `D05`, `D22` | muszkin | active | before implementation | Superseding decision record by muszkin |
| R06 | Before a translation begins, the app shows the external-processing notice and requires the user to confirm it. | translation start | `[DOCUMENT]` `D20` | muszkin | active | 2026-09-30 | Superseding decision record by muszkin |
| R07 | The app shows chapters, estimated tokens, indicative duration, and free-endpoint-limit warning before confirmation; it never falls back to a paid model. | translation start | `[DOCUMENT]` `D09`, `D16`, `D23` | muszkin | active | 2026-09-30 | Superseding decision record by muszkin |
| R08 | A chapter is attempted at most three times, then waits for explicit manual resume. | translation job | `[DOCUMENT]` `D16` | muszkin | superseded by R10 / D24 | 2026-09-16 | D24 supersedes R08 |
| R10 | Retry a chapter up to three times per currently free model, at most five models per run; expose attempts, preserve progress and allow manual model/context changes on resume. Never use paid fallback. | translation job | `[USER]` D24 | muszkin | active | 2026-09-30 | Superseding decision record by muszkin |
| R09 | A book's contextual-reference role never blocks Kindle or PocketBook delivery. | library and delivery | `[DOCUMENT]` `D14`, `D18`, `D19` | muszkin | active | 2026-09-30 | Superseding decision record by muszkin |

## Non-goals

| Id | We are not building | Why | Owner | Status | Review by | Required path to change |
|---|---|---|---|---|---|---|
| N01 | In-app translation editor | Keep version one focused on generating an EPUB. `[DOCUMENT]` `D08` | muszkin | active | 2026-09-30 | Superseding decision record by muszkin |
| N02 | OCR for scanned PDFs | Version one accepts EPUB only. `[DOCUMENT]` `D02`, `D08` | muszkin | active | 2026-09-30 | Superseding decision record by muszkin |
| N03 | Public sharing of translations | The feature is for private use. `[DOCUMENT]` `D01`, `D08` | muszkin | active | 2026-09-30 | Superseding decision record by muszkin |

## Decisions

| Id | Date | Decision | Why | Owner | Status | Review by | Required path to change |
|---|---|---|---|---|---|---|---|
| D01 | 2026-09-12 | Private-use translation feature. `[DOCUMENT]` `D01` | Bound the first product. | muszkin | active | 2026-09-30 | New decision record supersedes D01 |
| D02 | 2026-09-12 | Downloaded EPUB in; separate Polish EPUB out. `[DOCUMENT]` `D02` | Preserve a usable e-reader file. | muszkin | active | 2026-09-30 | New decision record supersedes D02 |
| D04 | 2026-09-12 | Translate in resumable chapter jobs. `[DOCUMENT]` `D04` | Show progress and recover from failures. | muszkin | active | 2026-09-30 | New decision record supersedes D04 |
| D05 | 2026-09-12 | Administrator configures the default model. `[DOCUMENT]` `D05` | Avoid task-level model choice in version one. | muszkin | task-level restriction superseded by D24; admin default retained | 2026-09-16 | D24 extends D05 |
| D06 | 2026-09-12 | Permit OpenRouter processing. `[DOCUMENT]` `D06` | External processing is acceptable for private use. | muszkin | active | 2026-09-30 | New decision record supersedes D06 |
| D07 | 2026-09-12 | Validate one useful 300-page target EPUB. `[DOCUMENT]` `D07` | Define first success. | muszkin | active | 2026-09-30 | New decision record supersedes D07 |
| D08 | 2026-09-12 | Exclude editing, OCR, and public sharing from version one. `[DOCUMENT]` `D08` | Keep the first slice narrow. | muszkin | active | 2026-09-30 | New decision record supersedes D08 |
| D09 | 2026-09-12 | Show estimate and require confirmation. `[DOCUMENT]` `D09` | Make job use explicit. | muszkin | active | 2026-09-30 | New decision record supersedes D09 |
| D11 | 2026-09-12 | Use manual open-model allowlist and zero-price check. `[DOCUMENT]` `D11` | Superseded because licence is no longer an eligibility criterion. | muszkin | superseded by D22 | 2026-09-12 | D22 supersedes D11 |
| D13 | 2026-09-12 | Persist a one-to-five chapter sample. `[DOCUMENT]` `D13` | Make context repeatable. | muszkin | active | 2026-09-30 | New decision record supersedes D13 |
| D14 | 2026-09-12 | Context status never blocks delivery. `[DOCUMENT]` `D14` | Contextual books are ordinary library items. | muszkin | active | 2026-09-30 | New decision record supersedes D14 |
| D15 | 2026-09-12 | User confirms contextual search result. `[DOCUMENT]` `D15` | Avoid automatic wrong-edition downloads. | muszkin | active | 2026-09-30 | New decision record supersedes D15 |
| D16 | 2026-09-12 | Stop after three failed chapter attempts. `[DOCUMENT]` `D16` | Never hide retries or switch to paid model. | muszkin | superseded by D24 | 2026-09-16 | D24 supersedes D16 |
| D17 | 2026-09-12 | Validate by 2026-09-30. `[DOCUMENT]` `D17` | Give the first test a deadline. | muszkin | active | 2026-09-30 | New decision record supersedes D17 |
| D18 | 2026-09-12 | Acquire contextual books as normal deliverable library items. `[DOCUMENT]` `D18` | Replaces D12's delivery restriction. | muszkin | active | 2026-09-30 | New decision record supersedes D18 |
| D19 | 2026-09-12 | Context affects translation, not delivery. `[DOCUMENT]` `D19` | Replaces D03's delivery implication. | muszkin | active | 2026-09-30 | New decision record supersedes D19 |
| D20 | 2026-09-12 | Require explicit external-processing confirmation. `[DOCUMENT]` `D20` | Make text transfer clear. | muszkin | active | 2026-09-30 | New decision record supersedes D20 |
| D21 | 2026-09-12 | Build version one as the validation vehicle. `[DOCUMENT]` `D21` | Explicitly accept the quality risk before test. | muszkin | active | 2026-09-30 | New decision record supersedes D21 |
| D22 | 2026-09-12 | Use all currently free models regardless of licence. `[DOCUMENT]` `D22` | Price, not licence, defines eligibility. | muszkin | active | before implementation | New decision record supersedes D22 |
| D23 | 2026-09-12 | Show job effort estimate without a time promise. `[DOCUMENT]` `D23` | Expose useful limits without a monetary estimate. | muszkin | active | 2026-09-30 | New decision record supersedes D23 |

## Riskiest assumptions

### D24 — Translation control extension, 2026-09-16

Owner/approval: muszkin, current conversation request to finish recovery of
Inhibitor Phase, automatically try another model after router failure, expose
logs and manual decisions, add references/glossary and export individual chapters.
This supersedes R08/D16 and the no-per-job-model restriction of D05. The bounded
implementation uses three attempts per model and at most five currently free
models per run; users may disable fallback or supply an ordered list. Administrator
default and the existing external-processing consent remain. Astra Medium refers
only to the Codex session, not to any translation provider. N01 (no in-app editor)
is unchanged: TXT/Markdown are read-only exports, not a translation editor.

Sampling implementation: default three chapters (Q01 implementation default),
stable preview before confirmation, up to 1,600 characters per sampled chapter
and 16,000 total reference characters to bound free-model context. Persist the
actual excerpts with the job; changing context applies only to unsaved segments.

| Id | Assumption | Importance | Evidence today | If false | Smallest test | Owner | By when | Result |
|---|---|---|---|---|---|---|---|---|
| A01 | Eligible free models produce useful Polish literary translation. | high | none `[ASSUMPTION]` `D07`, `D17`, `D22` | The feature does not meet its reading goal. | Translate the D17 target EPUB and evaluate 20 fragments. | muszkin | 2026-09-30 | accepted untested (`D21`) |
| A02 | EPUB extraction and rebuild preserve every chapter in the target file. | high | none `[ASSUMPTION]` `D02`, `D07`, `D17` | Result is not a usable EPUB. | Compare source and result chapter counts in D17 validation. | muszkin | 2026-09-30 | accepted untested (`D21`) |
| A03 | Existing metadata reliably identifies Polish EPUBs by the same author. | medium | weak `[ASSUMPTION]` `D18`, `D15` | Wrong context reduces translation consistency. | Select representative search results and verify the eligibility rule. | muszkin | 2026-09-30 | untested |

## Kill criteria

Stop further feature work if the D17 target EPUB loses chapters or muszkin does not judge the 20 selected fragments useful by 2026-09-30. `[DOCUMENT]` `D17`, `D21`

## Hypotheses to test

No synthetic walkthrough has been conducted.

## Open questions

| Id | Question | Blocking | Who can answer | Status |
|---|---|---|---|---|
| Q01 | What is the default contextual-reference chapter count when the user does not choose one? | no | muszkin | open |

## Definition of Ready addendum (existing + own)

- Compatibility: implementation must add API, SQLite, and SPA behavior through the OpenAPI-first and additive Liquibase paths; existing library, delivery, and user ownership flows remain compatible. `[PRODUCT]` `BACKWARD_COMPATIBILITY.md`
- Affected users and flows: authenticated library users; library, search, download, delivery, settings, and new translation jobs. `[PRODUCT]` `docs/ARCHITECTURE.md`
- The quality and EPUB-integrity assumptions are accepted untested by muszkin in D21, with the D17 kill criterion due 2026-09-30. `[DOCUMENT]` `D17`, `D21`
- The ticket-level tier is ready; the first quality and EPUB-integrity test remains accepted untested under D21.

## Collection plan

### First translation validation

- **What we need to know:** whether the target EPUB keeps all chapters and produces 20 useful selected Polish fragments.
- **Who can answer it:** muszkin.
- **How:** validation record — name the test EPUB, compare chapter counts, and record 20 fragment judgements.
- **Owner and by when:** muszkin; 2026-09-30.
- **Template:** `.ai/specs/research/templates/translation-validation.md`
