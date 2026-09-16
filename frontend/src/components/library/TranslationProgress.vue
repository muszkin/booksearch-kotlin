<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { TranslationService } from '@/api/generated'
import type { TranslationStatusResponse, TranslationDetails, TranslationOptions } from '@/api/generated'
import TranslationOptionsForm from './TranslationOptionsForm.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import AlertMessage from '@/components/base/AlertMessage.vue'

interface Props { job: TranslationStatusResponse; busy?: boolean; error?: string; author?: string }
const props = withDefaults(defineProps<Props>(), { busy: false, error: undefined })
const emit = defineEmits<{ resume: [options?: TranslationOptions]; cancel: []; retry: [] }>()
const canResume = computed(() => props.job.resumable && ['paused', 'failed'].includes(props.job.status))
const canCancel = computed(() => ['queued', 'paused'].includes(props.job.status))
const now = ref(Date.now())
const retryDeadline = computed(() => props.job.retryAt ? Date.parse(props.job.retryAt) : NaN)
const coolingDown = computed(() => retryDeadline.value > now.value)
let cooldownTimer: ReturnType<typeof setInterval> | undefined
function clearCooldownTimer() {
  if (cooldownTimer !== undefined) clearInterval(cooldownTimer)
  cooldownTimer = undefined
}
watch(() => props.job.retryAt, () => {
  clearCooldownTimer()
  now.value = Date.now()
  if (coolingDown.value) cooldownTimer = setInterval(() => {
    now.value = Date.now()
    if (!coolingDown.value) clearCooldownTimer()
  }, 1000)
}, { immediate: true })
onUnmounted(clearCooldownTimer)
function formatDeadline(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'brak danych' : date.toLocaleString('pl-PL', { timeZoneName: 'short' })
}
const scopeLabels = { platform: 'platforma', provider: 'dostawca', unknown: 'nieznany' }
const details = ref<TranslationDetails | null>(null)
const options = ref<TranslationOptions>({ autoFallback: true })
const expanded = ref(false)
const editing = ref(false)
const contextReady = ref(true)
const detailError = ref('')
const loadingDetails = ref(false)
const errors: Record<string, string> = {
  invalid_translation: 'Model zwrócił odpowiedź, której nie można bezpiecznie wstawić do EPUB-a. Starsza próba nie ma szczegółowego dziennika.',
  invalid_json: 'Odpowiedź modelu nie jest poprawną tablicą JSON.',
  item_count_mismatch: 'Model zwrócił inną liczbę fragmentów tekstu niż oczekiwano.',
  empty_translation: 'Model pozostawił pusty fragment tłumaczenia.',
  empty_response: 'Model nie zwrócił tekstu tłumaczenia.',
  output_truncated: 'Odpowiedź została ucięta przez limit wyjścia modelu.',
  request_timeout: 'OpenRouter nie odpowiedział w wyznaczonym czasie. Spróbuj innego darmowego modelu lub wznów później.',
  connection_failed: 'Nie udało się połączyć z OpenRouter. Sprawdź dostępność usługi i wznów później.',
  invalid_provider_response: 'Odpowiedź API OpenRouter miała nieprawidłową strukturę. Spróbuj innego darmowego modelu.',
  http_429: 'Limit OpenRouter. Ograniczenie liczby żądań może obejmować także inne darmowe modele.',
  model_no_longer_free: 'Wybrany model nie jest obecnie darmowy lub dostępny.',
  translation_request_failed: 'Żądanie do modelu nie powiodło się. Sprawdź historię prób i wybierz inny model przy wznowieniu.',
  server_restart: 'Serwer został uruchomiony ponownie. Zapisane fragmenty są zachowane.',
}
const reason = computed(() => props.job.error ? errors[props.job.error] ?? `Błąd: ${props.job.error}. Sprawdź historię prób.` : '')
async function loadDetails() {
  if (loadingDetails.value) return
  loadingDetails.value = true; detailError.value = ''
  try {
    const result = await TranslationService.getTranslationDetails(props.job.jobId)
    details.value = result
    if (!editing.value) options.value = { ...result.options, modelId: result.options.modelId ?? props.job.modelId }
  } catch { detailError.value = 'Nie udało się odczytać szczegółów. Spróbuj ponownie.' }
  finally { loadingDetails.value = false }
}
watch(() => [props.job.status, props.job.completedChapters], () => { if (expanded.value) void loadDetails() })
function saveFile(text: string, filename: string) {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }))
  const a = document.createElement('a'); a.href = url; a.download = filename; a.click(); URL.revokeObjectURL(url)
}
async function download(index: number, format: 'txt' | 'md') {
  try { saveFile(await TranslationService.exportTranslationChapter(props.job.jobId, index, format), `rozdzial-${index + 1}.${format}`) }
  catch { detailError.value = 'Nie udało się pobrać tłumaczenia rozdziału.' }
}
async function editOptions() { await loadDetails(); if (details.value) { editing.value = true; expanded.value = true } }
function resume() {
  if (coolingDown.value || props.busy || (editing.value && !contextReady.value)) return
  emit('resume', editing.value ? options.value : undefined); editing.value = false
}
</script>

<template>
  <section class="space-y-2 border-t border-zinc-700 p-4 text-sm text-zinc-300" aria-label="Translation progress">
    <div aria-live="polite" aria-atomic="true">
      <p>Translation: {{ job.status }} · {{ job.completedChapters }} / {{ job.totalChapters }} chapters</p>
      <p v-if="job.error" class="mt-1 text-amber-200">{{ reason }}</p>
      <p v-if="job.retryAt && Number.isFinite(retryDeadline) && ['queued', 'running', 'paused'].includes(job.status)" class="mt-1 text-amber-200">
        Limit OpenRouter. Następna próba nie wcześniej niż <time :datetime="job.retryAt">{{ formatDeadline(job.retryAt) }}</time>.
        <template v-if="job.status === 'queued' || job.status === 'running'">Tłumaczenie zostanie wznowione automatycznie.</template>
        <template v-else-if="coolingDown">Resume będzie dostępne po upływie tego czasu. Wznowienie wymaga kliknięcia.</template>
        <template v-else>Możesz wznowić tłumaczenie przyciskiem Resume.</template>
      </p>
      <p v-if="job.status === 'completed'" class="mt-1 text-emerald-300">
        Polish EPUB added as a separate library entry ({{ job.outputLibraryEntryId }}). Download or send it from its library card.
      </p>
    </div>
    <progress :value="job.completedChapters" :max="Math.max(job.totalChapters, 1)" class="h-2 w-full accent-violet-400" aria-label="Translated chapters" />
    <details :open="expanded" @toggle="expanded = ($event.target as HTMLDetailsElement).open; expanded && loadDetails()">
      <summary class="min-h-[44px] cursor-pointer py-3 text-violet-300 focus-visible:outline-2 focus-visible:outline-violet-400">Translation details</summary>
      <dl class="grid grid-cols-2 gap-2 break-words">
        <dt>Job</dt><dd class="break-all">{{ job.jobId }}</dd>
        <dt>Model</dt><dd class="break-all">{{ job.modelId }}</dd>
        <dt>Input tokens used</dt><dd>{{ job.actualInputTokens }}</dd>
        <dt>Output tokens used</dt><dd>{{ job.actualOutputTokens }}</dd>
        <template v-if="job.failedChapterIndex != null"><dt>Failed chapter</dt><dd>{{ job.failedChapterIndex + 1 }}</dd></template>
      </dl>
      <button type="button" class="my-3 text-violet-300 underline" :disabled="loadingDetails" @click="loadDetails">Odśwież rozdziały i dziennik</button>
      <p v-if="detailError" role="alert" class="text-rose-300">{{ detailError }}</p>
      <template v-if="details">
        <h3 class="my-2 font-semibold">Rozdziały i podgląd przekładu</h3>
        <p class="text-zinc-400">Numeracja obejmuje pliki w kolejności EPUB-a, także strony tytułowe. Eksport częściowy jest wyraźnie oznaczony.</p>
        <ol class="max-h-72 space-y-2 overflow-auto">
          <li v-for="chapter in details.chapters" :key="chapter.index" class="rounded border border-zinc-700 p-2">
            <p>{{ chapter.index + 1 }}. {{ chapter.href }} — {{ chapter.status }}</p>
            <p>{{ chapter.translatedSegments }}/{{ chapter.totalSegments }} fragmentów · {{ chapter.attempts }} prób</p>
            <div v-if="chapter.translatedSegments > 0" class="flex gap-4">
              <button type="button" class="text-violet-300 underline" @click="download(chapter.index, 'txt')">Pobierz TXT</button>
              <button type="button" class="text-violet-300 underline" @click="download(chapter.index, 'md')">Pobierz Markdown</button>
            </div>
          </li>
        </ol>
        <h3 class="my-3 font-semibold">Historia prób (ostatnie 300 żądań)</h3>
        <p v-if="!details.attempts.length">Starsze próby nie zawierają szczegółowego dziennika. Będzie zapisywany od następnego wznowienia.</p>
        <button type="button" class="mb-2 text-violet-300 underline" @click="saveFile(JSON.stringify(details.attempts, null, 2), 'translation-attempts.json')">Pobierz dziennik JSON</button>
        <ol class="max-h-80 space-y-2 overflow-auto">
          <li v-for="attempt in [...details.attempts].reverse()" :key="attempt.id" class="rounded border border-zinc-700 p-2">
            <p>{{ attempt.startedAt }} · rozdział {{ attempt.chapterIndex + 1 }}, fragment {{ attempt.segmentIndex + 1 }} · {{ attempt.status }}</p>
            <p class="break-all">{{ attempt.requestedModel }} → {{ attempt.actualModel ?? 'dostawca nie podał modelu' }}</p>
            <p v-if="attempt.message" class="text-amber-200">{{ attempt.message }} ({{ attempt.errorCode }})</p>
            <p v-if="attempt.retryAt">Następna próba nie wcześniej niż <time :datetime="attempt.retryAt">{{ formatDeadline(attempt.retryAt) }}</time>.</p>
            <p v-if="attempt.rateLimitScope">Zakres limitu: {{ scopeLabels[attempt.rateLimitScope] }}</p>
            <p v-if="attempt.rateLimitLimit != null">Limit: {{ attempt.rateLimitLimit }}</p>
            <p v-if="attempt.rateLimitRemaining != null">Pozostało: {{ attempt.rateLimitRemaining }}</p>
            <p>{{ attempt.itemCount }} elementów · tokeny {{ attempt.inputTokens }}/{{ attempt.outputTokens }} · zakończenie: {{ attempt.finishReason ?? 'brak danych' }}</p>
          </li>
        </ol>
      </template>
    </details>
    <div v-if="editing">
      <p class="text-amber-200">Nowy model i kontekst dotyczą wyłącznie pozostałych fragmentów. Dotychczasowy postęp zostaje zachowany.</p>
      <p>Wznowienie oznacza wysłanie pozostałego tekstu, glosariusza i wybranych fragmentów referencyjnych do OpenRouter.</p>
      <TranslationOptionsForm v-model="options" :source-id="job.sourceLibraryEntryId" :author="author" :disabled="busy" @ready="contextReady = $event" />
    </div>
    <AlertMessage v-if="error" variant="error" :message="error" />
    <div v-if="canResume || canCancel || error" class="flex flex-wrap gap-2">
      <BaseButton v-if="canResume" data-testid="translation-resume-btn" :disabled="busy || coolingDown || (editing && !contextReady)" @click="resume">Resume</BaseButton>
      <BaseButton v-if="canResume" variant="secondary" :disabled="busy" @click="editOptions">Zmień model / kontekst</BaseButton>
      <BaseButton v-if="canCancel" data-testid="translation-cancel-btn" variant="secondary" :disabled="busy" @click="emit('cancel')">Cancel translation</BaseButton>
      <BaseButton v-if="error" variant="secondary" :disabled="busy" @click="emit('retry')">Refresh status</BaseButton>
    </div>
  </section>
</template>
