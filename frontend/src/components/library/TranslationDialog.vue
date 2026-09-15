<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { TranslationEstimateResponse } from '@/api/generated'
import BaseButton from '@/components/base/BaseButton.vue'
import AlertMessage from '@/components/base/AlertMessage.vue'

interface Props {
  estimate: TranslationEstimateResponse | null
  loading?: boolean
  starting?: boolean
  error?: string | null
  title?: string
}
const props = withDefaults(defineProps<Props>(), { loading: false, starting: false, error: null, title: '' })
const emit = defineEmits<{ start: []; close: []; retry: [] }>()
const confirmed = ref(false)
const heading = ref<HTMLElement | null>(null)
const dialog = ref<HTMLElement | null>(null)
const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
const canStart = computed(() => !!props.estimate && confirmed.value && !props.loading && !props.starting && !props.error)
const duration = computed(() => props.estimate?.indicativeDuration === 'minutes_to_hours' ? 'minutes to hours (indicative)' : props.estimate?.indicativeDuration)

onMounted(() => heading.value?.focus())
onUnmounted(() => previousFocus?.focus())

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    if (!props.starting) emit('close')
  }
  if (event.key !== 'Tab') return
  const controls = Array.from(dialog.value?.querySelectorAll<HTMLElement>('*') ?? [])
    .filter((element) => element.tabIndex >= 0 && !element.hasAttribute('disabled'))
  const first = controls?.[0]
  const last = controls?.[controls.length - 1]
  if (event.shiftKey && (document.activeElement === first || document.activeElement === heading.value)) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
    <section
      ref="dialog" role="dialog" aria-modal="true" aria-labelledby="translation-heading"
      aria-describedby="translation-notice" :aria-busy="loading || starting"
      class="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-lg border border-zinc-700 bg-zinc-800 p-6 shadow-xl"
      @keydown="handleKeydown"
    >
      <h2 id="translation-heading" ref="heading" tabindex="-1" class="text-lg font-semibold text-zinc-100">Translate to Polish</h2>
      <p v-if="title" class="mt-1 break-words text-sm text-zinc-300">{{ title }}</p>
      <p v-if="loading" role="status" class="mt-4 text-zinc-300">Estimating translation…</p>
      <div v-if="estimate" class="mt-4 space-y-3 text-sm text-zinc-300">
        <dl class="grid grid-cols-2 gap-2">
          <dt>Chapters</dt><dd>{{ estimate.totalChapters }}</dd>
          <dt>Estimated input tokens</dt><dd>{{ estimate.estimatedInputTokens }}</dd>
          <dt>Selected model</dt><dd class="break-all">{{ estimate.modelId }}</dd>
          <dt>Estimated duration</dt><dd>{{ duration }}</dd>
        </dl>
        <p class="rounded-lg border border-amber-700 bg-amber-950/30 p-3 text-amber-200">{{ estimate.limitWarning }}</p>
      </div>
      <AlertMessage v-if="error" variant="error" :message="error" class="mt-4" />
      <BaseButton v-if="error" variant="secondary" class="mt-2" :disabled="loading || starting" @click="emit('retry')">Retry estimate</BaseButton>
      <div id="translation-notice" lang="pl" class="mt-4 space-y-2 text-sm text-zinc-200">
        <p>Treść EPUB-a oraz wybrany glosariusz i fragmenty kontekstu zostaną wysłane do OpenRouter w celu tłumaczenia</p>
        <p>W tej wersji nie wybrano materiałów kontekstowych.</p>
      </div>
      <label for="external-processing-confirmation" class="mt-4 flex min-h-[44px] cursor-pointer items-center gap-3 text-sm text-zinc-200">
        <input
          id="external-processing-confirmation" v-model="confirmed" type="checkbox"
          data-testid="external-processing-confirmation" :disabled="starting"
          class="h-5 w-5 shrink-0 accent-violet-400 focus-visible:outline-2 focus-visible:outline-violet-400"
        />
        I confirm sending this EPUB to OpenRouter for translation.
      </label>
      <div class="mt-5 flex flex-wrap gap-3">
        <BaseButton data-testid="translation-start-btn" :disabled="!canStart" :loading="starting" @click="canStart && emit('start')">Start translation</BaseButton>
        <BaseButton data-testid="translation-close-btn" variant="secondary" :disabled="starting" @click="emit('close')">Close</BaseButton>
      </div>
    </section>
  </div>
</template>
