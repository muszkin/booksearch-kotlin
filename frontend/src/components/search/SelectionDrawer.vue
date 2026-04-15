<script setup lang="ts">
import { watch, nextTick, ref } from 'vue'
import type { BookResult } from '@/api/generated'
import BaseButton from '@/components/base/BaseButton.vue'
import FormatBadge from './FormatBadge.vue'

interface Props {
  books: BookResult[]
  open: boolean
  kindleEnabled?: boolean
  pocketbookEnabled?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  close: []
  remove: [md5: string]
  clear: []
  'download-all': []
  'kindle-all': []
  'pocketbook-all': []
}>()

const panelRef = ref<HTMLElement | null>(null)

watch(
  () => props.open,
  async (isOpen) => {
    if (isOpen) {
      await nextTick()
      panelRef.value?.focus()
    }
  },
)

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    emit('close')
  }
}
</script>

<template>
  <!-- Desktop: fixed right panel -->
  <Transition name="drawer-desktop">
    <aside
      v-if="props.open"
      ref="panelRef"
      tabindex="-1"
      role="dialog"
      aria-modal="true"
      aria-label="Selected books"
      class="hidden lg:flex fixed top-0 right-0 h-full w-80 z-30 bg-zinc-900 border-l border-zinc-700 shadow-xl flex-col outline-none"
      @keydown="handleKeydown"
    >
      <header class="flex items-center justify-between border-b border-zinc-700 px-4 py-3">
        <h2 class="text-zinc-100 font-semibold text-lg">
          Selected ({{ props.books.length }})
        </h2>
        <BaseButton
          data-testid="clear-all-btn"
          variant="ghost"
          @click="emit('clear')"
        >
          Clear all
        </BaseButton>
      </header>

      <div class="flex-1 overflow-y-auto px-4 py-2">
        <p
          v-if="props.books.length === 0"
          class="text-zinc-500 text-sm py-8 text-center"
        >
          No books selected
        </p>
        <ul v-else class="flex flex-col gap-2">
          <li
            v-for="book in props.books"
            :key="book.md5"
            class="flex items-start justify-between gap-2 rounded-lg bg-zinc-800 p-3"
          >
            <div class="min-w-0 flex-1">
              <p class="text-zinc-100 text-sm font-medium truncate">{{ book.title }}</p>
              <p class="text-zinc-400 text-xs truncate">{{ book.author }}</p>
              <FormatBadge :format="book.format" class="mt-1" />
            </div>
            <BaseButton
              data-testid="remove-book-btn"
              variant="ghost"
              class="shrink-0 !px-2 !py-1 !min-h-[36px] !min-w-[36px]"
              @click="emit('remove', book.md5)"
            >
              <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
              <span class="sr-only">Remove {{ book.title }}</span>
            </BaseButton>
          </li>
        </ul>
      </div>

      <footer v-if="props.books.length > 0" class="border-t border-zinc-700 p-4 flex flex-col gap-2">
        <BaseButton
          data-testid="download-all-btn"
          variant="primary"
          class="w-full"
          @click="emit('download-all')"
        >
          Download All
        </BaseButton>
        <BaseButton
          v-if="props.kindleEnabled"
          data-testid="kindle-all-btn"
          variant="ghost"
          class="w-full"
          @click="emit('kindle-all')"
        >
          To Kindle All
        </BaseButton>
        <BaseButton
          v-if="props.pocketbookEnabled"
          data-testid="pocketbook-all-btn"
          variant="ghost"
          class="w-full"
          @click="emit('pocketbook-all')"
        >
          To PocketBook All
        </BaseButton>
      </footer>
    </aside>
  </Transition>

  <!-- Mobile: bottom sheet via Teleport -->
  <Teleport to="body">
    <Transition name="drawer-mobile">
      <div
        v-if="props.open"
        class="fixed inset-0 z-50 lg:hidden"
        role="dialog"
        aria-modal="true"
        aria-label="Selected books"
        @keydown="handleKeydown"
      >
        <div
          class="absolute inset-0 bg-black/50"
          @click="emit('close')"
        />

        <div
          class="absolute bottom-0 left-0 right-0 max-h-[60vh] rounded-t-xl bg-zinc-900 shadow-xl flex flex-col outline-none"
          tabindex="-1"
        >
          <div class="flex justify-center pt-2 pb-1">
            <div class="w-10 h-1 rounded-full bg-zinc-600" />
          </div>

          <header class="flex items-center justify-between border-b border-zinc-700 px-4 py-3">
            <h2 class="text-zinc-100 font-semibold text-lg">
              Selected ({{ props.books.length }})
            </h2>
            <BaseButton
              data-testid="clear-all-btn"
              variant="ghost"
              @click="emit('clear')"
            >
              Clear all
            </BaseButton>
          </header>

          <div class="flex-1 overflow-y-auto px-4 py-2">
            <p
              v-if="props.books.length === 0"
              class="text-zinc-500 text-sm py-8 text-center"
            >
              No books selected
            </p>
            <ul v-else class="flex flex-col gap-2">
              <li
                v-for="book in props.books"
                :key="book.md5"
                class="flex items-start justify-between gap-2 rounded-lg bg-zinc-800 p-3"
              >
                <div class="min-w-0 flex-1">
                  <p class="text-zinc-100 text-sm font-medium truncate">{{ book.title }}</p>
                  <p class="text-zinc-400 text-xs truncate">{{ book.author }}</p>
                  <FormatBadge :format="book.format" class="mt-1" />
                </div>
                <BaseButton
                  data-testid="remove-book-btn"
                  variant="ghost"
                  class="shrink-0 !px-2 !py-1 !min-h-[36px] !min-w-[36px]"
                  @click="emit('remove', book.md5)"
                >
                  <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                  <span class="sr-only">Remove {{ book.title }}</span>
                </BaseButton>
              </li>
            </ul>
          </div>

          <footer v-if="props.books.length > 0" class="border-t border-zinc-700 p-4 flex flex-col gap-2">
            <BaseButton
              data-testid="download-all-btn"
              variant="primary"
              class="w-full"
              @click="emit('download-all')"
            >
              Download All
            </BaseButton>
            <BaseButton
              v-if="props.kindleEnabled"
              data-testid="kindle-all-btn"
              variant="ghost"
              class="w-full"
              @click="emit('kindle-all')"
            >
              To Kindle All
            </BaseButton>
            <BaseButton
              v-if="props.pocketbookEnabled"
              data-testid="pocketbook-all-btn"
              variant="ghost"
              class="w-full"
              @click="emit('pocketbook-all')"
            >
              To PocketBook All
            </BaseButton>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.drawer-desktop-enter-active,
.drawer-desktop-leave-active {
  transition: transform 0.3s ease;
}

.drawer-desktop-enter-from,
.drawer-desktop-leave-to {
  transform: translateX(100%);
}

.drawer-mobile-enter-active,
.drawer-mobile-leave-active {
  transition: opacity 0.3s ease;
}

.drawer-mobile-enter-active > div:last-child,
.drawer-mobile-leave-active > div:last-child {
  transition: transform 0.3s ease;
}

.drawer-mobile-enter-from,
.drawer-mobile-leave-to {
  opacity: 0;
}

.drawer-mobile-enter-from > div:last-child,
.drawer-mobile-leave-to > div:last-child {
  transform: translateY(100%);
}
</style>
