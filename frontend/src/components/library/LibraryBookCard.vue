<script setup lang="ts">
import { computed } from 'vue'
import type { LibraryBook } from '@/api/generated/models/LibraryBook'
import type { DownloadStatusResponse } from '@/api/generated/models/DownloadStatusResponse'
import type { ConversionStatusResponse } from '@/api/generated/models/ConversionStatusResponse'
import type { DeliveryRecord } from '@/api/generated/models/DeliveryRecord'
import FormatBadge from '@/components/search/FormatBadge.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import DownloadProgressBar from './DownloadProgressBar.vue'

const AVAILABLE_FORMATS = ['epub', 'mobi', 'pdf'] as const

const props = defineProps<{
  book: LibraryBook
  downloadStatus?: DownloadStatusResponse
  conversionStatus?: ConversionStatusResponse
  deliveries: DeliveryRecord[]
  kindleEnabled: boolean
  pocketbookEnabled: boolean
}>()

const emit = defineEmits<{
  'download-file': []
  'start-download': []
  'convert': [targetFormat: string]
  'deliver': [device: string]
  'remove': []
}>()

const hasFile = computed(() => !!props.book.filePath)

const isDownloadActive = computed(() => {
  if (!props.downloadStatus) return false
  const terminalWithFile = props.downloadStatus.status === 'completed' && hasFile.value
  const failed = props.downloadStatus.status === 'failed'
  return !terminalWithFile && !failed
})

const isConversionActive = computed(() => {
  if (!props.conversionStatus) return false
  return !['completed', 'failed'].includes(props.conversionStatus.status)
})

const convertableFormats = computed(() =>
  AVAILABLE_FORMATS.filter((f) => f !== props.book.format.toLowerCase()),
)

const kindleDelivered = computed(() =>
  props.deliveries.some((d) => d.deviceType === 'kindle'),
)

const pocketbookDelivered = computed(() =>
  props.deliveries.some((d) => d.deviceType === 'pocketbook'),
)

const formattedDate = computed(() => {
  const date = new Date(props.book.addedAt)
  return date.toLocaleDateString()
})
</script>

<template>
  <article
    data-testid="library-book-card"
    class="bg-zinc-800 border border-zinc-700 rounded-lg hover:bg-zinc-800/50 transition-colors"
  >
    <div class="flex gap-4 p-4">
      <div class="shrink-0">
        <img
          v-if="props.book.coverUrl"
          :src="props.book.coverUrl"
          :alt="`Cover of ${props.book.title}`"
          class="w-20 h-30 rounded object-cover bg-zinc-700"
          loading="lazy"
        />
        <div
          v-else
          class="w-20 h-30 rounded bg-zinc-700"
          role="img"
          :aria-label="`No cover available for ${props.book.title}`"
        />
      </div>

      <div class="flex-1 min-w-0">
        <h3 class="text-zinc-100 font-semibold truncate">
          {{ props.book.title }}
        </h3>
        <p class="text-zinc-400 text-sm truncate">
          {{ props.book.author }}
        </p>
        <p
          v-if="props.book.publisher || props.book.year"
          class="text-zinc-500 text-sm"
        >
          <span v-if="props.book.publisher">{{ props.book.publisher }}</span>
          <span v-if="props.book.publisher && props.book.year"> · </span>
          <span v-if="props.book.year">{{ props.book.year }}</span>
        </p>

        <div class="flex items-center gap-2 mt-2 flex-wrap">
          <FormatBadge :format="props.book.format" />
          <span class="text-xs text-zinc-400">{{ props.book.fileSize }}</span>
          <span class="text-xs text-zinc-500">{{ formattedDate }}</span>

          <span
            v-if="kindleDelivered"
            data-testid="kindle-delivered"
            class="inline-flex items-center gap-1 text-xs text-emerald-400"
            title="Sent to Kindle"
          >
            <span class="w-2 h-2 rounded-full bg-emerald-400" aria-hidden="true" />
            Kindle
          </span>

          <span
            v-if="pocketbookDelivered"
            data-testid="pocketbook-delivered"
            class="inline-flex items-center gap-1 text-xs text-sky-400"
            title="Sent to PocketBook"
          >
            <span class="w-2 h-2 rounded-full bg-sky-400" aria-hidden="true" />
            PocketBook
          </span>
        </div>

        <div v-if="isDownloadActive && downloadStatus" class="mt-3">
          <DownloadProgressBar
            :status="downloadStatus.status"
            :progress="downloadStatus.progress"
            :error="downloadStatus.error ?? undefined"
          />
        </div>

        <p
          v-if="isConversionActive && conversionStatus"
          class="mt-2 text-xs text-violet-400"
        >
          Converting to {{ conversionStatus.targetFormat }}...
        </p>
      </div>
    </div>

    <div class="flex items-center gap-2 px-4 py-3 border-t border-zinc-700 flex-wrap">
      <BaseButton
        v-if="hasFile"
        data-testid="download-file-btn"
        variant="primary"
        class="text-xs px-3 py-1"
        @click="emit('download-file')"
      >
        Download
      </BaseButton>

      <BaseButton
        v-if="!hasFile && !isDownloadActive"
        data-testid="start-download-btn"
        variant="primary"
        class="text-xs px-3 py-1"
        @click="emit('start-download')"
      >
        Start Download
      </BaseButton>

      <template v-for="format in convertableFormats" :key="format">
        <BaseButton
          :data-testid="`convert-${format}-btn`"
          variant="secondary"
          class="text-xs px-3 py-1"
          :disabled="isConversionActive"
          @click="emit('convert', format)"
        >
          To {{ format.toUpperCase() }}
        </BaseButton>
      </template>

      <BaseButton
        v-if="kindleEnabled"
        data-testid="send-kindle-btn"
        variant="ghost"
        class="text-xs px-3 py-1"
        @click="emit('deliver', 'kindle')"
      >
        Send to Kindle
      </BaseButton>

      <BaseButton
        v-if="pocketbookEnabled"
        data-testid="send-pocketbook-btn"
        variant="ghost"
        class="text-xs px-3 py-1"
        @click="emit('deliver', 'pocketbook')"
      >
        Send to PocketBook
      </BaseButton>

      <BaseButton
        data-testid="remove-btn"
        variant="danger"
        class="text-xs px-3 py-1 ml-auto"
        @click="emit('remove')"
      >
        Remove
      </BaseButton>
    </div>
  </article>
</template>
