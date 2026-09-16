<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { ApiError, DownloadService, SearchService, TranslationService } from '@/api/generated'
import type { BookResult, LibraryBook, TranslationModel, TranslationOptions } from '@/api/generated'

const props = defineProps<{ sourceId: number; author?: string; disabled?: boolean }>()
const emit = defineEmits<{ ready: [ready: boolean]; modelChange: [model?: string] }>()
const options = defineModel<TranslationOptions>({ required: true })
const models = ref<TranslationModel[]>([])
const references = ref<LibraryBook[]>([])
const query = ref('')
const results = ref<BookResult[]>([])
const busy = ref(false)
const error = ref('')
const notice = ref('')
const preview = ref('')
const previewError = ref('')
const previewLoading = ref(false)
let previewRequest = 0
let firstPreview = true
let disposed = false
let timer: ReturnType<typeof setTimeout> | undefined
onUnmounted(() => { disposed = true; clearTimeout(timer) })
const inputClass = 'w-full rounded border border-zinc-600 bg-zinc-900 p-2 text-zinc-100'

watch(() => JSON.stringify([options.value.referenceLibraryIds ?? [], options.value.referenceChapters ?? 3]), async () => {
  const current = ++previewRequest
  const usePersisted = firstPreview
  firstPreview = false
  preview.value = ''; previewError.value = ''; previewLoading.value = false
  if (!options.value.referenceLibraryIds?.length) { emit('ready', true); return }
  if (usePersisted && options.value.referenceText) { preview.value = options.value.referenceText; emit('ready', true); return }
  emit('ready', false); previewLoading.value = true
  try {
    const result = await TranslationService.previewTranslationContext(props.sourceId, {
      referenceLibraryIds: options.value.referenceLibraryIds, referenceChapters: options.value.referenceChapters,
    })
    if (disposed || current !== previewRequest) return
    preview.value = result.referenceText ?? ''
    emit('ready', !!preview.value)
  } catch { if (!disposed && current === previewRequest) previewError.value = 'Nie udało się przygotować próbki. Sprawdź polskie EPUB-y i wybierz referencje ponownie.' }
  finally { if (current === previewRequest) previewLoading.value = false }
}, { immediate: true })

async function refresh() {
  try {
    const [m, r] = await Promise.all([TranslationService.listTranslationModels(), TranslationService.listTranslationReferences(props.sourceId)])
    if (disposed) return
    models.value = m; references.value = r
  } catch { error.value = 'Nie udało się odczytać modeli lub książek referencyjnych. Odśwież listę.' }
}
onMounted(refresh)
function update<K extends keyof TranslationOptions>(key: K, value: TranslationOptions[K]) {
  options.value = { ...options.value, [key]: value }
  if (key === 'modelId') emit('modelChange', typeof value === 'string' ? value : undefined)
}
function selectReference(id: number, checked: boolean) {
  const ids = options.value.referenceLibraryIds ?? []
  update('referenceLibraryIds', checked ? [...ids, id] : ids.filter(x => x !== id))
}
async function search() {
  if (!query.value.trim() || busy.value) return
  busy.value = true; error.value = ''; results.value = []; notice.value = 'Wyszukiwanie polskich EPUB-ów…'
  try {
    const link = query.value.match(/\/md5\/([a-f0-9]{32})/i)
    if (link?.[1]) { await download(link[1]); return }
    const started = await SearchService.submitSearch(`${query.value} ${props.author ?? ''}`.trim(), 'pl', 'epub')
    const poll = async () => {
      try {
        const status = await SearchService.getSearchStatus(started.jobId)
        if (disposed) return
        results.value = status.results.filter(book => !props.author || book.author.trim().toLowerCase() === props.author.trim().toLowerCase())
        if (['queued', 'running'].includes(status.status)) timer = setTimeout(poll, 3000)
        else { busy.value = false; notice.value = results.value.length ? 'Wybierz książkę do pobrania jako referencję.' : 'Brak wyników tego autora. Spróbuj innego tytułu.' }
      } catch { busy.value = false; error.value = 'Nie udało się odczytać wyników wyszukiwania.' }
    }
    await poll()
  } catch { busy.value = false; error.value = 'Nie udało się wyszukać książki.' }
}
async function download(md5: string) {
  busy.value = true; error.value = ''; notice.value = 'Pobieranie referencji do biblioteki. Nie zostanie wysłana na czytnik.'
  try {
    const started = await TranslationService.downloadTranslationReference(props.sourceId, md5)
    const poll = async () => {
      try {
        const status = await DownloadService.getDownloadStatus(started.jobId)
        if (disposed) return
        if (status.status === 'completed') {
          await refresh()
          if (disposed) return
          busy.value = false
          const reference = references.value.find(book => book.bookMd5 === md5)
          if (reference && !(options.value.referenceLibraryIds ?? []).includes(reference.id)) selectReference(reference.id, true)
          notice.value = reference ? 'Referencja pobrana i wybrana.' : 'Pobrano książkę. Referencja musi być polskim EPUB-em tego samego autora.'
        } else if (['failed', 'cancelled'].includes(status.status)) { busy.value = false; error.value = 'Pobieranie nie powiodło się. Szczegóły w zakładce pobierania.' }
        else timer = setTimeout(poll, 3000)
      } catch { busy.value = false; error.value = 'Nie udało się odczytać postępu pobierania.' }
    }
    await poll()
  } catch (e) {
    busy.value = false
    error.value = e instanceof ApiError && e.status === 404 ? 'Ten odnośnik nie jest jeszcze w katalogu. Wyszukaj książkę po tytule.' : 'Nie udało się rozpocząć pobierania. Referencja musi być polskim EPUB-em tego samego autora.'
  }
}
</script>

<template>
  <fieldset :disabled="disabled" class="my-4 space-y-4 text-sm text-zinc-200">
    <legend class="font-semibold">Model i kontekst tłumaczenia</legend>
    <label class="block">Preferowany darmowy model
      <select :class="inputClass" :value="options.modelId ?? ''" @change="update('modelId', ($event.target as HTMLSelectElement).value || null)">
        <option value="">Domyślny model administratora</option>
        <option v-for="model in models" :key="model.id" :value="model.id">{{ model.name }} ({{ model.id }})</option>
      </select>
    </label>
    <label class="flex gap-2"><input type="checkbox" :checked="options.autoFallback !== false" @change="update('autoFallback', ($event.target as HTMLInputElement).checked)">Automatycznie próbuj kolejnych darmowych modeli po błędzie (maks. 5 modeli, 3 próby na model).</label>
    <label class="block">Kolejność modeli zapasowych (opcjonalnie, identyfikatory po jednym w wierszu)
      <textarea :class="inputClass" rows="2" :value="options.fallbackModelIds?.join('\n')" placeholder="Puste: automatyczny wybór z bieżącej listy darmowych modeli" @input="update('fallbackModelIds', ($event.target as HTMLTextAreaElement).value.split('\n').map(x => x.trim()).filter(Boolean))" />
    </label>
    <div>
      <p class="font-semibold">Polskie książki referencyjne tego samego autora</p>
      <p class="text-zinc-400">Wybrane fragmenty pomagają zachować nazewnictwo i styl przekładu. Próbka zostanie zapisana z zadaniem.</p>
      <label v-for="book in references" :key="book.id" class="my-2 flex gap-2">
        <input type="checkbox" :checked="options.referenceLibraryIds?.includes(book.id)" @change="selectReference(book.id, ($event.target as HTMLInputElement).checked)">{{ book.title }} — {{ book.author }}
      </label>
      <p v-if="!references.length" class="my-2 text-zinc-400">Brak pasujących referencji w bibliotece.</p>
      <button type="button" class="text-violet-300 underline" @click="refresh">Odśwież listę referencji i modeli</button>
      <label class="mt-2 block">Liczba losowych rozdziałów z każdej referencji
        <input type="number" min="1" max="5" :class="inputClass" :value="options.referenceChapters ?? 3" @input="update('referenceChapters', Number(($event.target as HTMLInputElement).value))">
      </label>
      <label class="mt-2 block">Dodaj referencję: tytuł lub odnośnik Anna’s Archive
        <input v-model="query" :class="inputClass" placeholder="Tytuł polskiego przekładu lub /md5/…" @keydown.enter.prevent="search">
      </label>
      <button type="button" :disabled="busy" class="mt-2 text-violet-300 underline disabled:opacity-50" @click="search">Wyszukaj / pobierz do biblioteki</button>
      <p v-if="notice" role="status" class="mt-2">{{ notice }}</p>
      <ul class="space-y-2"><li v-for="book in results" :key="book.md5">{{ book.title }} — {{ book.author }} <button type="button" :disabled="busy" class="text-violet-300 underline" @click="download(book.md5)">Pobierz referencję</button></li></ul>
      <p v-if="previewLoading" role="status">Przygotowywanie próbki referencyjnej…</p>
      <p v-if="previewError" role="alert" class="text-rose-300">{{ previewError }}</p>
      <div v-if="preview" class="mt-3">
        <p class="font-semibold">Próbka, która zostanie wysłana do modelu</p>
        <pre class="max-h-48 overflow-auto whitespace-pre-wrap break-words rounded bg-zinc-900 p-2">{{ preview }}</pre>
      </div>
    </div>
    <label class="block">Glosariusz (np. Conjoiners → Spójni)
      <textarea :class="inputClass" rows="4" maxlength="12000" :value="options.glossary ?? ''" @input="update('glossary', ($event.target as HTMLTextAreaElement).value)" />
    </label>
    <label class="block">Kontekst, nazwy własne i uwagi do przekładu
      <textarea :class="inputClass" rows="3" maxlength="4000" :value="options.notes ?? ''" @input="update('notes', ($event.target as HTMLTextAreaElement).value)" />
    </label>
    <p v-if="error" role="alert" class="text-rose-300">{{ error }}</p>
  </fieldset>
</template>
