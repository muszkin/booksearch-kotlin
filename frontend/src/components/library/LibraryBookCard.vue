<script setup lang="ts">
import { computed } from 'vue'
import type { LibraryBook } from '@/api/generated/models/LibraryBook'
import type { DownloadStatusResponse } from '@/api/generated/models/DownloadStatusResponse'
import type { ConversionStatusResponse } from '@/api/generated/models/ConversionStatusResponse'
import type { DeliveryRecord } from '@/api/generated/models/DeliveryRecord'
import FormatBadge from '@/components/search/FormatBadge.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import DownloadProgressBar from './DownloadProgressBar.vue'
import LibraryCoverImage from './LibraryCoverImage.vue'

const AVAILABLE_FORMATS = ['epub', 'mobi', 'pdf'] as const

const props = defineProps<{
  book: LibraryBook
  downloadStatus?: DownloadStatusResponse
  conversionStatus?: ConversionStatusResponse
  deliveries: DeliveryRecord[]
  kindleEnabled: boolean
  pocketbookEnabled: boolean
  deliveryLoading?: boolean
  descriptionLoading?: boolean
  descriptionMissing?: boolean
  canRegenerate?: boolean
}>()

const emit = defineEmits<{
  'download-file': []
  'start-download': []
  'convert': [targetFormat: string]
  'deliver': [device: string]
  'remove': []
  'regenerate-description': []
}>()

const hasFile = computed(() => !!props.book.filePath)

/**
 * A generated description is labelled. Unlabelled guesswork sitting beside real publisher
 * copy is indistinguishable from it.
 */
const isGenerated = computed(() => props.book.descriptionSource === 'openrouter')

const hasDescription = computed(() => props.book.description.trim().length > 0)

const showDescription = computed(
  () => hasDescription.value || props.descriptionLoading === true || props.descriptionMissing === true,
)

const isDownloadActive = computed(() => {
  if (!props.downloadStatus) return false
  return !['completed', 'failed', 'cancelled'].includes(props.downloadStatus.status)
})

const showDownloadStatus = computed(() => {
  if (!props.downloadStatus) return false
  return !hasFile.value
})

const downloadAvailabilityNote = computed(() => {
  if (props.downloadStatus?.status === 'failed') {
    return 'Download failed. Retry before converting or sending this book.'
  }
  if (['completed', 'cancelled'].includes(props.downloadStatus?.status ?? '')) {
    return 'The file is not available. Retry the download to unlock file actions.'
  }
  return 'The book is still downloading. File actions will unlock when it is ready.'
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
        <LibraryCoverImage
          :library-id="props.book.id"
          :fallback-url="props.book.coverUrl || undefined"
          :alt="`Cover of ${props.book.title}`"
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

        <div v-if="showDownloadStatus && downloadStatus" class="mt-3">
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

    <div
      v-if="showDescription"
      data-testid="description-body"
      class="px-4 pb-4 text-sm text-zinc-300"
      :aria-busy="props.descriptionLoading === true"
    >
      <p v-if="!hasDescription && props.descriptionLoading" class="text-zinc-500">
        Looking for a description…
      </p>

      <p v-else-if="!hasDescription" class="text-zinc-500">
        No description available for this book.
      </p>

      <template v-else>
        <p
          v-if="isGenerated"
          data-testid="description-generated-label"
          class="mb-1 text-xs text-amber-400"
        >
          AI-generated summary — may be inaccurate
        </p>
        <p class="whitespace-pre-line">{{ props.book.description }}</p>
      </template>

      <button
        v-if="props.canRegenerate"
        type="button"
        data-testid="description-regenerate"
        title="Replace the stored description with a freshly generated one, for everyone"
        class="mt-2 text-xs text-zinc-400 underline hover:text-zinc-200 disabled:opacity-50"
        :disabled="props.descriptionLoading"
        @click="emit('regenerate-description')"
      >
        {{ hasDescription ? 'Wrong description? Regenerate with AI' : 'Generate one with AI' }}
      </button>
    </div>

    <div class="flex items-center gap-2 px-4 py-3 border-t border-zinc-700 flex-wrap">
      <p
        v-if="!hasFile"
        data-testid="download-pending-note"
        class="basis-full text-xs text-zinc-400"
      >
        {{ downloadAvailabilityNote }}
      </p>

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
          :disabled="!hasFile || isConversionActive"
          :title="hasFile ? undefined : 'Available after download completes'"
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
        :loading="props.deliveryLoading"
        :disabled="!hasFile || props.deliveryLoading"
        :title="hasFile ? undefined : 'Available after download completes'"
        @click="emit('deliver', 'kindle')"
      >
        Send to Kindle
      </BaseButton>

      <BaseButton
        v-if="pocketbookEnabled"
        data-testid="send-pocketbook-btn"
        variant="ghost"
        class="text-xs px-3 py-1"
        :loading="props.deliveryLoading"
        :disabled="!hasFile || props.deliveryLoading"
        :title="hasFile ? undefined : 'Available after download completes'"
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
