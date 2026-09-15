<script setup lang="ts">
import { computed } from 'vue'
import type { TranslationStatusResponse } from '@/api/generated'
import BaseButton from '@/components/base/BaseButton.vue'
import AlertMessage from '@/components/base/AlertMessage.vue'

interface Props { job: TranslationStatusResponse; busy?: boolean; error?: string }
const props = withDefaults(defineProps<Props>(), { busy: false, error: undefined })
const emit = defineEmits<{ resume: []; cancel: []; retry: [] }>()
const canResume = computed(() => props.job.resumable && ['paused', 'failed'].includes(props.job.status))
const canCancel = computed(() => ['queued', 'paused'].includes(props.job.status))
</script>

<template>
  <section class="space-y-2 border-t border-zinc-700 p-4 text-sm text-zinc-300" aria-label="Translation progress">
    <div aria-live="polite" aria-atomic="true">
      <p>Translation: {{ job.status }} · {{ job.completedChapters }} / {{ job.totalChapters }} chapters</p>
      <p v-if="job.error" class="mt-1 text-amber-200">Translation stopped: {{ job.error }}</p>
      <p v-if="job.status === 'completed'" class="mt-1 text-emerald-300">
        Polish EPUB added as a separate library entry ({{ job.outputLibraryEntryId }}). Download or send it from its library card.
      </p>
    </div>
    <progress :value="job.completedChapters" :max="Math.max(job.totalChapters, 1)" class="h-2 w-full accent-violet-400" aria-label="Translated chapters" />
    <details>
      <summary class="min-h-[44px] cursor-pointer py-3 text-violet-300 focus-visible:outline-2 focus-visible:outline-violet-400">Translation details</summary>
      <dl class="grid grid-cols-2 gap-2 break-words">
        <dt>Job</dt><dd class="break-all">{{ job.jobId }}</dd>
        <dt>Model</dt><dd class="break-all">{{ job.modelId }}</dd>
        <dt>Input tokens used</dt><dd>{{ job.actualInputTokens }}</dd>
        <dt>Output tokens used</dt><dd>{{ job.actualOutputTokens }}</dd>
        <template v-if="job.failedChapterIndex != null"><dt>Failed chapter</dt><dd>{{ job.failedChapterIndex + 1 }}</dd></template>
      </dl>
    </details>
    <AlertMessage v-if="error" variant="error" :message="error" />
    <div v-if="canResume || canCancel || error" class="flex flex-wrap gap-2">
      <BaseButton v-if="canResume" data-testid="translation-resume-btn" :disabled="busy" @click="emit('resume')">Resume</BaseButton>
      <BaseButton v-if="canCancel" data-testid="translation-cancel-btn" variant="secondary" :disabled="busy" @click="emit('cancel')">Cancel translation</BaseButton>
      <BaseButton v-if="error" variant="secondary" :disabled="busy" @click="emit('retry')">Refresh status</BaseButton>
    </div>
  </section>
</template>
