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
| GET | `/api/search` | Yes | Search Anna's Archive. Query params: `q`, `lang` (pl/en/de), `format` (epub/mobi/pdf). |

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

## Error Format

All errors return a consistent JSON envelope:

```json
{
  "status": 401,
  "message": "Invalid or expired token"
}
```

Standard HTTP status codes: 400, 401, 403, 404, 409, 422, 502, 500.
