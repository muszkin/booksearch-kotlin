<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import AlertMessage from '@/components/base/AlertMessage.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import LibraryBookCard from '@/components/library/LibraryBookCard.vue'
import LibraryBookCardSkeleton from '@/components/library/LibraryBookCardSkeleton.vue'
import PaginationControls from '@/components/library/PaginationControls.vue'
import { useLibraryStore } from '@/stores/library'
import apiClient from '@/api/client'

const store = useLibraryStore()
const selectedIds = ref(new Set<number>())
const deliveryLoading = reactive(new Map<number, boolean>())

const hasSelection = computed(() => selectedIds.value.size > 0)
const selectionCount = computed(() => selectedIds.value.size)
const allSelected = computed(() =>
  store.books.length > 0 && store.books.every((b) => selectedIds.value.has(b.id)),
)

function toggleSelect(bookId: number) {
  const next = new Set(selectedIds.value)
  if (next.has(bookId)) {
    next.delete(bookId)
  } else {
    next.add(bookId)
  }
  selectedIds.value = next
}

function toggleSelectAll() {
  if (allSelected.value) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(store.books.map((b) => b.id))
  }
}

async function handleBatchDownload() {
  const ids = Array.from(selectedIds.value)
  const response = await apiClient.post('/library/batch-download', { ids }, { responseType: 'blob' })
  const blob = response.data as Blob
  const url = URL.createObjectURL(blob)

  const disposition = response.headers['content-disposition'] as string | undefined
  const filenameMatch = disposition?.match(/filename="?([^"]+)"?/)
  const filename = filenameMatch?.[1] ?? 'books.zip'

  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()

  URL.revokeObjectURL(url)
}

onMounted(() => {
  store.fetchLibrary(1)
  store.fetchDeviceSettings()
  store.fetchDeliveries()
})

onUnmounted(() => {
  store.cleanup()
})

function handleRetry() {
  store.fetchLibrary(store.pagination.page)
}

function handlePageChange(page: number) {
  store.fetchLibrary(page)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function handleDownloadFile(bookId: number) {
  store.downloadFile(bookId)
}

function handleStartDownload(bookMd5: string) {
  store.startDownloadPolling(bookMd5)
}

function handleConvert(bookId: number, targetFormat: string) {
  store.startConversionPolling(bookId, targetFormat as 'epub' | 'mobi' | 'pdf')
}

async function handleDeliver(bookId: number, device: string) {
  deliveryLoading.set(bookId, true)
  try {
    await store.deliverBook(bookId, device as 'kindle' | 'pocketbook')
  } catch {
    // Error already set in store
  } finally {
    deliveryLoading.set(bookId, false)
  }
}

function handleRemove(bookId: number) {
  if (window.confirm('Are you sure you want to remove this book from your library?')) {
    store.removeBook(bookId)
  }
}
</script>

<template>
  <PageHeader title="Library" />

  <div class="p-6">
    <!-- Error state -->
    <div v-if="store.error && !store.loading">
      <AlertMessage variant="error" :message="store.error" />
      <div class="mt-4">
        <BaseButton
          data-testid="retry-btn"
          variant="primary"
          @click="handleRetry"
        >
          Retry
        </BaseButton>
      </div>
    </div>

    <!-- Loading state -->
    <div v-else-if="store.loading" aria-busy="true">
      <div class="flex flex-col gap-4">
        <LibraryBookCardSkeleton v-for="n in 6" :key="n" />
      </div>
    </div>

    <!-- Results state -->
    <div v-else-if="store.hasBooks">
      <div data-testid="library-book-list" class="flex flex-col gap-4">
        <div v-for="book in store.books" :key="book.id" class="flex items-start gap-3">
          <label class="flex items-center min-h-[44px] min-w-[44px] cursor-pointer pt-4">
            <input
              type="checkbox"
              data-testid="library-select-checkbox"
              :checked="selectedIds.has(book.id)"
              class="w-5 h-5 rounded border-zinc-600 bg-zinc-700 text-violet-400 focus:ring-violet-400 focus:ring-offset-zinc-900"
              :aria-label="`Select ${book.title}`"
              @change="toggleSelect(book.id)"
            />
          </label>
          <div class="flex-1 min-w-0">
            <LibraryBookCard
              :book="book"
              :download-status="store.activeDownloads.get(book.bookMd5)"
              :conversion-status="store.activeConversions.get(book.id)"
              :deliveries="store.deliveries.get(book.bookMd5) ?? []"
              :kindle-enabled="store.deviceSettings.kindle"
              :pocketbook-enabled="store.deviceSettings.pocketbook"
              :delivery-loading="deliveryLoading.get(book.id) ?? false"
              @download-file="handleDownloadFile(book.id)"
              @start-download="handleStartDownload(book.bookMd5)"
              @convert="handleConvert(book.id, $event)"
              @deliver="handleDeliver(book.id, $event)"
              @remove="handleRemove(book.id)"
            />
          </div>
        </div>
      </div>

      <div
        v-if="hasSelection"
        data-testid="selection-toolbar"
        class="fixed bottom-6 left-1/2 -translate-x-1/2 z-40 flex items-center gap-3 rounded-xl bg-zinc-800 border border-zinc-700 px-5 py-3 shadow-2xl"
      >
        <span class="text-sm text-zinc-300">{{ selectionCount }} selected</span>
        <BaseButton
          data-testid="select-all-btn"
          variant="ghost"
          class="text-xs"
          @click="toggleSelectAll"
        >
          {{ allSelected ? 'Deselect All' : 'Select All' }}
        </BaseButton>
        <BaseButton
          data-testid="batch-download-btn"
          variant="primary"
          class="text-xs"
          @click="handleBatchDownload"
        >
          Download as ZIP
        </BaseButton>
      </div>

      <PaginationControls
        v-if="store.pagination.totalPages > 1"
        :current-page="store.pagination.page"
        :total-pages="store.pagination.totalPages"
        @page-change="handlePageChange"
      />
    </div>

    <!-- Empty state -->
    <div v-else>
      <EmptyState
        title="Your library is empty"
        description="Books you download will appear here."
      >
        <template #action>
          <RouterLink
            :to="{ name: 'search' }"
            class="inline-flex items-center gap-2 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-zinc-900 transition-colors hover:bg-accent-hover"
          >
            Search for books
          </RouterLink>
        </template>
      </EmptyState>
    </div>
  </div>
</template>
