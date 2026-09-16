# API Reference

Base URL: `http://localhost:8080/api`

All authenticated endpoints require the `Authorization: Bearer <accessToken>` header. Admin endpoints additionally require the `is_super_admin` claim in the JWT.

## Auth

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | No | Register a new account. First user becomes super-admin. |
| POST | `/api/auth/login` | No | Authenticate with email and password. Returns access + refresh tokens. |
| POST | `/api/auth/refresh` | No | Exchange a valid refresh token for a new access + refresh token pair. |
| POST | `/api/auth/password-reset-request` | No | Request a password-reset link sent via SMTP. |
| POST | `/api/auth/password-reset` | No | Reset password using the token from the e-mail link. |
| PUT | `/api/auth/password` | Yes | Change the authenticated user's own password. |
| POST | `/api/auth/logout` | Yes | Invalidate the current refresh token. |

## Admin (super-admin only)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| PUT | `/api/admin/registration` | Admin | Enable or disable public registration. |
| GET | `/api/admin/users` | Admin | List all registered users. |
| POST | `/api/admin/users` | Admin | Create a new user account (invite). |
| PUT | `/api/admin/users/{id}/password` | Admin | Force-change another user's password. |

## Search

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/search` | Yes | Start an async search job against Anna's Archive. Query params: `q`, `lang` (pl/en/de), `ext` (epub/mobi/pdf), `maxPages` (1-10, default 3). Returns `202` with a `jobId`. |
| GET | `/api/search/status/{jobId}` | Yes | Poll a search job. Status is one of `queued`, `scraping`, `completed`, `failed`; results are returned once completed. |

Search is asynchronous because scraping runs through FlareSolverr's headless browser and
routinely takes longer than the 120 second proxy read timeout, which surfaced as Cloudflare
`error 524` in production.

## Library

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/library` | Yes | List the authenticated user's library. Query params: `page`, `pageSize`. |
| POST | `/api/library` | Yes | Add a book to the library (by MD5 + format). |
| DELETE | `/api/library/{id}` | Yes | Remove a library entry. |
| GET | `/api/library/{id}/file` | Yes | Download the book file for a library entry. |
| GET | `/api/library/check` | Yes | Check ownership for a list of MD5 hashes. Query param: `md5` (comma-separated). |

## Download

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/download/{md5}` | Yes | Start an async download job for the given book MD5. Query param: `format`. |
| GET | `/api/download/status/{jobId}` | Yes | Poll the status of a download job. |

## Convert

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/convert/{libraryId}` | Yes | Start an async format conversion (epub↔mobi). Query param: `targetFormat`. |
| GET | `/api/convert/status/{jobId}` | Yes | Poll the status of a conversion job. |

## Deliver

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/deliver/{libraryId}` | Yes | Send a library book to a device via SMTP. Query param: `device` (kindle/pocketbook). |

## Deliveries

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/deliveries` | Yes | List all deliveries for the authenticated user. |
| GET | `/api/deliveries/{bookMd5}` | Yes | List deliveries for a specific book. |

## Settings

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/settings` | Yes | Get all device settings (kindle, pocketbook) for the user. |
| GET | `/api/settings/{device}` | Yes | Get settings for a specific device. |
| PUT | `/api/settings/{device}` | Yes | Save SMTP + recipient settings for a device. |
| DELETE | `/api/settings/{device}` | Yes | Delete all settings for a device. |

## Mirror

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/mirrors/current` | Yes | Get the currently active Anna's Archive mirror URL. |

## System

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/health` | No | Health check. Returns `{"status": "ok"}`. |
| GET | `/api/openapi.json` | No | OpenAPI 3.0 specification (JSON). |
| GET | `/swagger-ui` | No | Swagger UI for interactive API exploration. |

## EPUB translation control

All translation endpoints require JWT and scope library entries/jobs to the authenticated user.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/api/translation/{libraryId}/references` | Downloaded Polish EPUBs by the source author in the user's library. |
| POST | `/api/translation/{libraryId}/context-preview` | Preview deterministic chapter samples before confirmation; accepts options, returns server-generated `referenceText`, sends no text to a model. |
| POST | `/api/translation/{libraryId}/references/{md5}/download` | Validate indexed metadata, then queue the existing download workflow; never automatically deliver to a device. Unknown links must first be indexed through search. |
| POST | `/api/translation/{libraryId}` | Start with `externalProcessingConfirmed: true` and optional `options`. |
| GET | `/api/translation/jobs` | Rediscover owned queued, running, paused and completed jobs, newest first. Completed jobs remain discoverable for chapter export. |
| GET | `/api/translation/jobs/{jobId}/details` | Persisted options, sampled context, chapter progress and per-request diagnostics. |
| GET | `/api/translation/jobs/{jobId}/chapters/{index}/export?format=txt` | Download saved translated text; `md` is also supported. Index is zero-based EPUB spine order (including front matter). Partial chapters are explicitly marked; no untranslated source is substituted. |
| POST | `/api/translation/jobs/{jobId}/resume` | Empty body preserves existing behavior; an optional `TranslationOptions` body changes model/context for unsaved segments only. |

Options: `modelId`, `autoFallback` (default true), `fallbackModelIds` (ordered,
at most 10 IDs), `referenceLibraryIds` (at most 5), `referenceChapters` (1–5,
default 3), `glossary` (12,000 characters), and `notes` (4,000 characters).
Lists are bounded: reference candidates are selected from the latest 200 owned
EPUBs by that author; discovery includes all active jobs plus the latest 100
completed jobs; details return the most recently updated 300 attempt records.
Earlier attempt files remain in the private workspace.

`referenceText` is generated server-side: up to 1,600 characters from each
sampled chapter and 16,000 characters overall. Samples persist across resume
unless reference selection/count changes. Reference catalog metadata and EPUB
language are validated before sending context.

Each chapter has at most three attempts per model and five models per run.
Automatic fallback chooses currently free text models unless an ordered list
is provided; every completion request rechecks pricing. Invalid output is
retried in smaller text-node batches without changing existing checkpoint
filenames. Authentication/billing failures pause immediately. Disabling
fallback uses only the selected model. No paid model is selected automatically.

Attempt diagnostics include timestamp, chapter/segment index, requested and
actual model, item count, token usage, finish reason and safe error detail.
Codes distinguish `invalid_json`, `item_count_mismatch`, `empty_translation`,
`empty_response`, `invalid_characters`, `output_truncated` and `http_<status>`.
Transport diagnostics distinguish `request_timeout`, `connection_failed` and
`invalid_provider_response` (an invalid API envelope, not invalid translation JSON).
Messages do not include raw provider payloads or exception details.
Logs do not contain source text, raw model replies or credentials. Historical
attempts from before this release cannot be reconstructed. Existing workspaces
without options/log files remain resumable and exportable. No database migration
or additional OpenRouter key is required; descriptions and translations use the
same configured key. Device delivery behavior is unchanged.

## Error Format

All errors return a consistent JSON envelope:

```json
{
  "status": 401,
  "message": "Invalid or expired token"
}
```

Standard HTTP status codes: 400, 401, 403, 404, 409, 422, 502, 500.
