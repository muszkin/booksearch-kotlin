# Development Guide

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 21+ | Backend compilation and runtime |
| Node.js | 22+ | Frontend tooling |
| pnpm | latest | Frontend package manager |
| Docker & Docker Compose | latest | FlareSolverr, Mailpit, production builds |

## Local Development

### 1. Start infrastructure services

```bash
docker compose up -d flaresolverr mailpit
```

- **FlareSolverr** — available at `http://localhost:8191` (Cloudflare bypass proxy)
- **Mailpit** — SMTP on port `1025`, web UI at `http://localhost:8025`

### 2. Run the backend

```bash
./gradlew :backend:run
```

The backend starts on `http://localhost:8080` with the default configuration from `backend/src/main/resources/application.yaml`. Environment variables can be overridden via the `.env` file or directly:

```bash
JWT_SECRET=my-dev-secret ./gradlew :backend:run
```

### 3. Run the frontend dev server

```bash
cd frontend
pnpm install
pnpm dev
```

Vite starts on `http://localhost:5173` with HMR and proxies API calls to the backend.

## Testing

### Backend tests (164 tests)

```bash
./gradlew :backend:test
```

Tests use an in-memory SQLite database and do not require external services.

### Frontend tests (159 tests)

```bash
cd frontend
pnpm test
```

Uses Vitest with jsdom. No browser or network required.

### Full test suite

```bash
./gradlew :backend:test && cd frontend && pnpm test
```

## Building

### Fat JAR (backend + embedded frontend)

```bash
./gradlew :backend:buildFatJar
```

The Gradle build automatically runs `pnpm install && pnpm build` in the frontend directory and copies the output into `backend/src/main/resources/static/` before packaging.

Output: `backend/build/libs/booksearch-v2.jar`

Run it:

```bash
java -jar backend/build/libs/booksearch-v2.jar
```

### Docker image

```bash
docker build -t booksearch-v2 .
```

The multi-stage Dockerfile handles the full build (JDK 21 + Node 22 builder, then JRE 21 + Calibre runtime).

## OpenAPI Client Regeneration

When the backend OpenAPI spec changes (`backend/src/main/resources/openapi/api.yaml`), regenerate the TypeScript client:

```bash
cd frontend
pnpm run generate:api
```

This produces typed service classes and model interfaces in `frontend/src/api/generated/`.

## Project Structure

```
booksearch-v2/
├── backend/
│   └── src/main/kotlin/pl/fairydeck/booksearch/
│       ├── api/          # Ktor route handlers
│       ├── service/      # Business logic
│       ├── repository/   # JOOQ database access
│       └── infrastructure/  # HTTP clients, config, parsing
├── frontend/
│   └── src/
│       ├── api/          # Axios client + generated OpenAPI client
│       ├── components/   # Reusable Vue components
│       ├── stores/       # Pinia state management
│       ├── views/        # Page-level components
│       └── router/       # Vue Router configuration
├── docs/                 # Architecture and API documentation
├── docker-compose.yml    # Production stack
├── Dockerfile            # Multi-stage build
└── .github/workflows/    # CI/CD pipelines
```

## Database Migrations

Liquibase migrations live in `backend/src/main/resources/db/changelog/migrations/` and are applied automatically on startup. To add a new migration:

1. Create a new SQL file with the next sequence number (e.g., `011-create-new-table.sql`)
2. Add the changeset reference to `backend/src/main/resources/db/changelog/changelog.yml`
3. Restart the application — Liquibase runs pending migrations on boot

## Debugging

All HTTP requests are logged via the RequestLoggerPlugin (inspired by Zalando Logbook). Each request is tagged with a unique `X-Request-Id` header, propagated through MDC for correlated log entries.
