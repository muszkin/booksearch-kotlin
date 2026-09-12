# Backward compatibility

## Protected surfaces

| Surface | Breaking change | Required path |
| --- | --- | --- |
| HTTP API | Removing or renaming an endpoint, request field, response field, query parameter, error status, or changing auth semantics | Update OpenAPI and documentation, retain a compatible path or version the API, and add regression coverage. |
| OpenAPI and generated clients | Editing generated frontend or Kotlin output, or changing a schema without regenerating consumers | Change `backend/src/main/resources/openapi/api.yaml`, regenerate clients during the build, and test both sides. |
| JWT and RBAC | Changing token claims, issuer/audience rules, expiry behavior, or super-admin authorization | Provide a rollout/migration path for existing sessions and test authorized and unauthorized behavior. |
| SQLite schema | Editing an applied migration, dropping/renaming data, or changing ownership constraints | Add a new sequential Liquibase migration with a forward-compatible data path; never rewrite applied migrations. |
| Runtime configuration | Renaming/removing an environment variable or changing its meaning/default | Keep a compatible alias or document a migration; update `.env.example`, `application.yaml`, Compose, and deployment documentation. |
| Deployment | Changing container ports, health checks, persistent data location, image name, or Compose service dependencies | Preserve compatibility where practical and document operator actions before release. |
| SPA behavior | Removing a route, changing navigation/authorization behavior, or altering a persisted user workflow | Provide a transition path and cover guards, stores, and affected views with tests. |

## Review rule

Any pull request touching a protected surface must identify it in the pull
request description, explain compatibility impact, and include the appropriate
migration, documentation, and test evidence. A breaking change without an
approved rollout is a blocker.
