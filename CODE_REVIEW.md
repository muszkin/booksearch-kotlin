# Code review guide

## Review priorities

1. **Correctness and contracts.** Preserve the HTTP behavior documented by
   `backend/src/main/resources/openapi/api.yaml` and `docs/API.md`, including
   authentication, authorization, status codes, and the `ErrorResponse`
   envelope.
2. **Security and data isolation.** Check JWT-protected routes, super-admin
   guards, password and SMTP handling, request validation, and that queries
   scope user-owned data to the authenticated principal.
3. **Persistence.** Review Liquibase changes as immutable, sequential SQL
   migrations under `backend/src/main/resources/db/changelog/migrations/`.
   Do not edit generated JOOQ sources; preserve the migration-to-codegen path.
4. **Layering.** Ktor routes stay in `api/`, business behavior in `service/`,
   database access in `repository/`, and external integrations in
   `infrastructure/`. Routes do not call repositories directly.
5. **Frontend.** Vue components use Composition API and typed interfaces.
   Pinia stores use setup-style definitions. API changes originate in OpenAPI;
   regenerate `frontend/src/api/generated/` rather than editing it manually.
6. **Operational behavior.** Review environment-variable defaults,
   Docker Compose effects, logging, health checks, and external dependencies
   (FlareSolverr, Calibre, SMTP) when touched.

## Evidence

Run the configured validation gate before approval:

- `./gradlew :backend:test`
- `pnpm --dir frontend test -- --run`
- `./gradlew build`

Require focused regression tests for changed behavior. User-facing changes
normally require browser QA under the repository QA gate.

## Severity

- **Blocker:** data exposure/loss, broken authentication or authorization,
  incompatible public API or migration, or a failing required validation gate.
- **Major:** incorrect user-visible behavior, missing error handling, broken
  contract generation, or a regression without adequate coverage.
- **Minor:** maintainability, clarity, consistency, or test-quality issue that
  does not alter correctness today.
