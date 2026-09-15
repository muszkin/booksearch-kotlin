# EPUB Translation through OpenRouter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tłumaczyć pobrany angielski EPUB na osobny polski EPUB przez trwałe zadania OpenRouter.

**Architecture:** Route przekazuje żądanie do `TranslationService`; osobne repozytoria zapisują zadanie i rozdziały w SQLite. Workspace tłumaczy wyłącznie spine XHTML, a wynik jest publikowany atomowo jako nowy wpis biblioteki.

**Tech Stack:** Kotlin, Ktor, JOOQ, Liquibase, SQLite, epub4j, OpenAPI, Vue 3, Pinia, Vitest.

**Spec:** `.ai/specs/2026-09-12-epub-translation-openrouter.md`

## Global Constraints

- Klucz `OPENROUTER_API_KEY` pochodzi wyłącznie ze zmiennej środowiskowej i nie trafia do logów, SQLite ani odpowiedzi API.
- Model musi mieć bieżącą cenę tekstowego requestu równą `0`; brak fallbacku płatnego.
- Zgoda przed startem jest niezaznaczona; UI pokazuje rozdziały, tokeny, orientacyjny czas i limity.
- Trzy próby rozdziału, potem `paused` i ręczne resume.
- Endpointy są JWT/user-scoped, konfiguracja modelu tylko dla super-admina.
- OpenAPI aktualizować przed frontendem; Liquibase tylko przez nowe migracje.
- Poza zakresem: kontekst autora, glosariusz, OCR, edytor, udostępnianie publiczne.

## File structure

| Path | Responsibility |
|---|---|
| `backend/src/main/kotlin/pl/fairydeck/booksearch/infrastructure/OpenRouterConfig.kt` | Konfiguracja API bez wycieku sekretu. |
| `backend/src/main/kotlin/pl/fairydeck/booksearch/infrastructure/OpenRouterClient.kt` | Lista darmowych modeli i chat completion. |
| `backend/src/main/kotlin/pl/fairydeck/booksearch/repository/TranslationJobRepository.kt` | Owner-scoped job state. |
| `backend/src/main/kotlin/pl/fairydeck/booksearch/repository/TranslationChapterRepository.kt` | Próby i postęp rozdziałów. |
| `backend/src/main/kotlin/pl/fairydeck/booksearch/service/EpubTranslationWorkspace.kt` | Spine XHTML, rebuild i walidacja. |
| `backend/src/main/kotlin/pl/fairydeck/booksearch/service/TranslationService.kt` | State machine, retry, publish. |
| `backend/src/main/kotlin/pl/fairydeck/booksearch/api/TranslationRoutes.kt` | Kontrakty JWT. |
| `frontend/src/stores/translation.ts` | Estimate i polling. |
| `frontend/src/components/library/TranslationDialog.vue` | Zgoda i start. |

## Task 1: Konfiguracja i darmowy model

**Files:**
- Create: `backend/src/main/kotlin/pl/fairydeck/booksearch/infrastructure/OpenRouterConfig.kt`
- Create: `backend/src/main/kotlin/pl/fairydeck/booksearch/infrastructure/OpenRouterClient.kt`
- Create: `backend/src/test/kotlin/pl/fairydeck/booksearch/infrastructure/OpenRouterClientTest.kt`
- Modify: `application.yaml`, `.env.example`, `docker-compose.yml`, `README.md`

**Interfaces:** Produces `listFreeTextModels(): List<OpenRouterModel>` and `translate(modelId, prompt): OpenRouterCompletion`.

- [ ] **Step 1: Write failing tests.**

```kotlin
@Test fun `filters paid models`() = runTest {
  mock.enqueue(modelsResponse(free = true, paid = true))
  assertThat(client.listFreeTextModels().map { it.id }).containsExactly("free")
}
@Test fun `error omits prompt and key`() = runTest {
  val e = assertFailsWith<OpenRouterException> { client.translate("free", "secret prose") }
  assertThat(e.message).doesNotContain("secret prose", "test-key")
}
```

- [ ] **Step 2: Run:** `./gradlew :backend:test --tests '*OpenRouterClientTest'`. Expected: FAIL.
- [ ] **Step 3: Implement:** call `GET /api/v1/models`, require text output plus zero prompt/completion/request prices; call chat completions with explicit model and no fallback.
- [ ] **Step 4: Run:** `./gradlew :backend:test --tests '*OpenRouterClientTest'`. Expected: PASS.
- [ ] **Step 5: Commit:** `git add backend/src/main/kotlin/pl/fairydeck/booksearch/infrastructure backend/src/test/kotlin/pl/fairydeck/booksearch/infrastructure backend/src/main/resources/application.yaml .env.example docker-compose.yml README.md && git commit -m "feat: add OpenRouter model client"`.

## Task 2: Migracje i repozytoria zadań

**Files:**
- Create: `backend/src/main/resources/db/changelog/migrations/013-create-translation-jobs-table.sql`
- Create: `backend/src/main/resources/db/changelog/migrations/014-create-translation-chapters-table.sql`
- Create: `backend/src/main/kotlin/pl/fairydeck/booksearch/repository/TranslationJobRepository.kt`
- Create: `backend/src/main/kotlin/pl/fairydeck/booksearch/repository/TranslationChapterRepository.kt`
- Create: `backend/src/test/kotlin/pl/fairydeck/booksearch/repository/TranslationJobRepositoryTest.kt`
- Modify: `changelog.yml`, `build.gradle.kts`, `SystemConfigRepository.kt`

**Interfaces:** `create(userId, sourceLibraryEntryId, modelId): String`, `findByIdAndUserId(id, userId)`, `pauseInterruptedJobs()`, `getTranslationDefaultModel()`.

- [ ] **Step 1: Write failing tests.**

```kotlin
@Test fun `job is invisible to another user`() {
  val id = jobs.create(1, 10, "free")
  assertThat(jobs.findByIdAndUserId(id, 2)).isNull()
}
@Test fun `startup pauses running jobs`() {
  jobs.markRunning(id); jobs.pauseInterruptedJobs()
  assertThat(jobs.findByIdAndUserId(id, 1)!!.status).isEqualTo("paused")
}
```

- [ ] **Step 2: Run:** `./gradlew :backend:test --tests '*TranslationJobRepositoryTest'`. Expected: FAIL.
- [ ] **Step 3: Implement:** tables for UUID job, owner, source library entry, model, status, chapter counts, attempts, tokens, workspace, output entry, error, timestamps; unique chapter index per job; add both tables to JOOQ includes.
- [ ] **Step 4: Run:** `./gradlew :backend:test --tests '*TranslationJobRepositoryTest' && ./gradlew :backend:compileKotlin`. Expected: PASS.
- [ ] **Step 5: Commit:** `git add backend/src/main/resources/db/changelog backend/src/main/kotlin/pl/fairydeck/booksearch/repository build.gradle.kts && git commit -m "feat: persist translation jobs"`.

## Task 3: Workspace EPUB i wykonawca

**Files:**
- Create: `backend/src/main/kotlin/pl/fairydeck/booksearch/service/EpubTranslationWorkspace.kt`
- Create: `backend/src/main/kotlin/pl/fairydeck/booksearch/service/TranslationService.kt`
- Create: `backend/src/test/kotlin/pl/fairydeck/booksearch/service/EpubTranslationWorkspaceTest.kt`
- Create: `backend/src/test/kotlin/pl/fairydeck/booksearch/service/TranslationServiceTest.kt`
- Modify: `LibraryService.kt`, `Application.kt`

**Interfaces:** `create(source, jobId): TranslationPlan`; `estimate/start/status/resume/cancel` on `TranslationService`.

- [ ] **Step 1: Write failing tests.**

```kotlin
@Test fun `rebuild preserves spine count`() {
  val plan = workspace.create(fixture, "job")
  workspace.replaceSegment(plan.segments.first(), "Polski tekst")
  assertThat(readSpine(workspace.publish(plan))).hasSize(readSpine(fixture).size)
}
@Test fun `third transient failure pauses`() = runTest {
  every { client.translate(any(), any()) } throws TimeoutException()
  service.start(1, 10, true); advanceUntilIdle()
  assertThat(service.status(1, jobId).status).isEqualTo("paused")
}
```

- [ ] **Step 2: Run:** `./gradlew :backend:test --tests '*EpubTranslationWorkspaceTest' --tests '*TranslationServiceTest'`. Expected: FAIL.
- [ ] **Step 3: Implement:** process only reading-order XHTML text nodes, preserve all remaining archive resources, split only on block boundaries, validate output chapter count, use one shared worker, persist each attempt, and pause after three failures.
- [ ] **Step 4: Implement atomic publication:** rebuild temporary EPUB, hash and move it, then create new `books` and `user_library` records with language `pl`; leave source untouched.
- [ ] **Step 5: Run:** `./gradlew :backend:test --tests '*EpubTranslationWorkspaceTest' --tests '*TranslationServiceTest' --tests '*LibraryServiceTest' --tests '*DeliveryServiceTest'`. Expected: PASS.
- [ ] **Step 6: Commit:** `git add backend/src/main/kotlin/pl/fairydeck/booksearch/service backend/src/test/kotlin/pl/fairydeck/booksearch/service backend/src/main/kotlin/pl/fairydeck/booksearch/Application.kt && git commit -m "feat: add resumable EPUB translation"`.

## Task 4: Kontrakty OpenAPI i endpointy

**Files:**
- Create: `backend/src/main/kotlin/pl/fairydeck/booksearch/api/TranslationRoutes.kt`
- Create: `backend/src/test/kotlin/pl/fairydeck/booksearch/api/TranslationRoutesTest.kt`
- Modify: `AdminRoutes.kt`, `Application.kt`, `backend/src/main/resources/openapi/api.yaml`
- Modify: `frontend/src/api/generated/**` via generator

**Interfaces:** Produces models, estimate, start/status/resume/cancel and super-admin translation configuration routes.

- [ ] **Step 1: Write failing route tests.**

```kotlin
@Test fun `start requires confirmation`() = testApplication {
  val response = client.post("/api/translation/10") {
    bearerAuth(owner); setBody("{\\"externalProcessingConfirmed\\":false}")
  }
  assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
}
@Test fun `job is hidden from another owner`() = testApplication {
  assertEquals(HttpStatusCode.NotFound,
    client.get("/api/translation/jobs/$jobId") { bearerAuth(other) }.status)
}
```

- [ ] **Step 2: Run:** `./gradlew :backend:test --tests '*TranslationRoutesTest'`. Expected: FAIL.
- [ ] **Step 3: Implement:** add schemas first, then routes; return `202` on start, `409` for duplicate active source job, `422` for invalid preconditions, and `404` for non-owned jobs; protect admin config with `requireSuperAdmin`.
- [ ] **Step 4: Run:** `./gradlew :backend:openApiGenerate :backend:test --tests '*TranslationRoutesTest'`. Expected: PASS.
- [ ] **Step 5: Commit:** `git add backend/src/main/kotlin/pl/fairydeck/booksearch/api backend/src/test/kotlin/pl/fairydeck/booksearch/api backend/src/main/resources/openapi/api.yaml frontend/src/api/generated && git commit -m "feat: expose translation APIs"`.

## Task 5: Dialog, polling i UI administratora

**Files:**
- Create: `frontend/src/stores/translation.ts`
- Create: `frontend/src/components/library/TranslationDialog.vue`
- Create: `frontend/src/components/library/TranslationProgress.vue`
- Create: `frontend/src/components/admin/TranslationModelSettings.vue`
- Create: tests beside all components
- Modify: `LibraryBookCard.vue`, `LibraryView.vue`, `AdminView.vue`

**Interfaces:** Produces `useTranslationStore.start(libraryId)`, `poll(jobId)`, `resume(jobId)`, `stopPolling(jobId)`.

- [ ] **Step 1: Write failing UI tests.**

```ts
it('disables start until consent', async () => {
  const wrapper = mount(TranslationDialog, { props: { estimate } })
  expect(wrapper.get('[data-testid="translation-start-btn"]').attributes('disabled')).toBeDefined()
  await wrapper.get('[data-testid="external-processing-confirmation"]').setValue(true)
  expect(wrapper.get('[data-testid="translation-start-btn"]').attributes('disabled')).toBeUndefined()
})
```

- [ ] **Step 2: Run:** `pnpm --dir frontend test -- --run src/components/library/__tests__/TranslationDialog.test.ts`. Expected: FAIL.
- [ ] **Step 3: Implement:** show action only for local EPUB; render exact consent, estimate and limit warning; poll every five seconds; stop on terminal/paused; refresh library after completion; model selector accepts only server-returned free models.
- [ ] **Step 4: Run:** `pnpm --dir frontend test -- --run src/components/library/__tests__/TranslationDialog.test.ts src/components/library/__tests__/TranslationProgress.test.ts src/components/admin/__tests__/TranslationModelSettings.test.ts && pnpm --dir frontend build`. Expected: PASS.
- [ ] **Step 5: Commit:** `git add frontend/src && git commit -m "feat: add translation library workflow"`.

## Final verification

- [ ] `./gradlew :backend:test`
- [ ] `pnpm --dir frontend test -- --run`
- [ ] `./gradlew build`
- [ ] Manualnie: EPUB pokazuje dialog, non-EPUB nie; zgoda blokuje start; trzecia porażka pauzuje; resume nie wybiera modelu płatnego; wynik można pobrać i wysłać.
- [ ] Do 2026-09-30 zapisz test docelowego EPUB-a w `.ai/specs/research/templates/translation-validation.md` przed pracą nad kontekstem autora.

## Self-review

- **Spec coverage:** zadania pokrywają sekret środowiskowy, darmowy model, trwałość, EPUB fidelity, retry/resume, publikację, OpenAPI, owner isolation, admin UI i dostępność.
- **Scope:** kontekst autora pozostaje osobnym późniejszym zakresem.
- **Type consistency:** `OpenRouterClient → TranslationService → TranslationRoutes → generated client → translation Pinia store`; identyfikatory zadań są UUID string.

