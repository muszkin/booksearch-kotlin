<script setup lang="ts">
import { computed } from 'vue'
import type { BookResult } from '@/api/generated/models/BookResult'
import FormatBadge from './FormatBadge.vue'
import OwnershipBadge from './OwnershipBadge.vue'
import BaseButton from '@/components/base/BaseButton.vue'

interface Props {
  book: BookResult
  selected: boolean
  downloadLoading?: boolean
  deliveryLoading?: boolean
  kindleEnabled?: boolean
  pocketbookEnabled?: boolean
  descriptionOpen?: boolean
  descriptionLoading?: boolean
  descriptionMissing?: boolean
  description?: string
  descriptionSource?: string
  isbn?: string
  canRegenerate?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'toggle-select': []
  download: []
  deliver: [device: string]
  'toggle-description': []
  'regenerate-description': []
}>()

/**
 * A generated description is labelled. Anna's Archive holds a great many obscure titles a
 * model can only guess at, and unlabelled guesswork sitting beside real publisher copy is
 * indistinguishable from it.
 */
const isGenerated = computed(() => props.descriptionSource === 'openrouter')

const borderColorMap: Record<string, string> = {
  exact: 'border-l-4 border-l-emerald-400',
  title: 'border-l-4 border-l-amber-400',
  author: 'border-l-4 border-l-sky-400',
}

const borderClass = computed(() => borderColorMap[props.book.matchType] ?? '')

function onCheckboxChange() {
  emit('toggle-select')
}
</script>

<template>
  <article
    data-testid="book-card"
    :class="[
      'bg-zinc-800 border border-zinc-700 rounded-lg hover:bg-zinc-800/50 transition-colors',
      borderClass,
    ]"
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
          <span class="text-xs text-zinc-500">{{ props.book.language }}</span>
        </div>

        <div class="mt-2">
          <OwnershipBadge
            :match-type="props.book.matchType"
            :owned-formats="props.book.ownedFormats"
          />
        </div>
      </div>
    </div>

    <div class="flex items-center gap-3 px-4 py-3 border-t border-zinc-700">
      <label class="flex items-center min-h-[44px] min-w-[44px] cursor-pointer">
        <input
          type="checkbox"
          :checked="props.selected"
          class="w-5 h-5 rounded border-zinc-600 bg-zinc-700 text-violet-400 focus:ring-violet-400 focus:ring-offset-zinc-900"
          :aria-label="`Select ${props.book.title}`"
          @change="onCheckboxChange"
        />
      </label>
      <div class="flex gap-2 ml-auto">
        <BaseButton
          data-testid="download-btn"
          variant="primary"
          class="text-xs px-3 py-1"
          :loading="props.downloadLoading"
          :disabled="props.downloadLoading"
          @click="emit('download')"
        >
          Download
        </BaseButton>
        <BaseButton
          v-if="props.kindleEnabled"
          data-testid="send-kindle-btn"
          variant="ghost"
          class="text-xs px-3 py-1"
          :loading="props.deliveryLoading"
          :disabled="props.deliveryLoading"
          @click="emit('deliver', 'kindle')"
        >
          To Kindle
        </BaseButton>
        <BaseButton
          v-if="props.pocketbookEnabled"
          data-testid="send-pocketbook-btn"
          variant="ghost"
          class="text-xs px-3 py-1"
          :loading="props.deliveryLoading"
          :disabled="props.deliveryLoading"
          @click="emit('deliver', 'pocketbook')"
        >
          To PocketBook
        </BaseButton>
      </div>
    </div>
  
    <div class="mt-3">
      <button
        type="button"
        data-testid="description-toggle"
        class="text-sm text-zinc-400 underline hover:text-zinc-200"
        :aria-expanded="props.descriptionOpen === true"
        @click="emit('toggle-description')"
      >
        {{ props.descriptionOpen ? 'Hide description' : 'Description' }}
      </button>

      <div
        v-if="props.descriptionOpen"
        data-testid="description-body"
        class="mt-2 text-sm text-zinc-300"
        :aria-busy="props.descriptionLoading === true"
      >
        <p v-if="props.descriptionLoading" class="text-zinc-500">Looking for a description…</p>

        <p v-else-if="props.descriptionMissing" class="text-zinc-500">
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
          <p class="whitespace-pre-line">{{ props.description }}</p>
          <p v-if="props.isbn" class="mt-1 text-xs text-zinc-500">ISBN {{ props.isbn }}</p>

          <button
            v-if="props.canRegenerate"
            type="button"
            data-testid="description-regenerate"
            title="Replace the stored description with a freshly generated one, for everyone"
            class="mt-2 text-xs text-zinc-400 underline hover:text-zinc-200"
            @click="emit('regenerate-description')"
          >
            Wrong description? Regenerate with AI
          </button>
        </template>
      </div>
    </div>
  </article>
</template>
