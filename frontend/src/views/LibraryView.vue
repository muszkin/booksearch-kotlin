<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import AlertMessage from '@/components/base/AlertMessage.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import LibraryBookCard from '@/components/library/LibraryBookCard.vue'
import LibraryBookCardSkeleton from '@/components/library/LibraryBookCardSkeleton.vue'
import PaginationControls from '@/components/library/PaginationControls.vue'
import { useLibraryStore } from '@/stores/library'

const store = useLibraryStore()

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
  try {
    await store.deliverBook(bookId, device as 'kindle' | 'pocketbook')
  } catch {
    // Error already set in store
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
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <LibraryBookCardSkeleton v-for="n in 6" :key="n" />
      </div>
    </div>

    <!-- Results state -->
    <div v-else-if="store.hasBooks">
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <LibraryBookCard
          v-for="book in store.books"
          :key="book.id"
          :book="book"
          :download-status="store.activeDownloads.get(book.bookMd5)"
          :conversion-status="store.activeConversions.get(book.id)"
          :deliveries="store.deliveries.get(book.bookMd5) ?? []"
          :kindle-enabled="store.deviceSettings.kindle"
          :pocketbook-enabled="store.deviceSettings.pocketbook"
          @download-file="handleDownloadFile(book.id)"
          @start-download="handleStartDownload(book.bookMd5)"
          @convert="handleConvert(book.id, $event)"
          @deliver="handleDeliver(book.id, $event)"
          @remove="handleRemove(book.id)"
        />
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
