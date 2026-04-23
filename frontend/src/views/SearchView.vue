<script setup lang="ts">
import { ref, reactive, watch, computed, onMounted } from 'vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import SearchToolbar from '@/components/search/SearchToolbar.vue'
import BookCard from '@/components/search/BookCard.vue'
import BookCardSkeleton from '@/components/search/BookCardSkeleton.vue'
import SelectionDrawer from '@/components/search/SelectionDrawer.vue'
import SelectionFab from '@/components/search/SelectionFab.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import AlertMessage from '@/components/base/AlertMessage.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import { useSearchStore } from '@/stores/search'
import { useSelectionStore } from '@/stores/selection'
import { useSettingsStore } from '@/stores/settings'
import { useDownloadQueueStore } from '@/stores/download-queue'
import { DownloadService } from '@/api/generated'

const searchStore = useSearchStore()
const selectionStore = useSelectionStore()
const settingsStore = useSettingsStore()
const queueStore = useDownloadQueueStore()

const drawerOpen = ref(false)
const downloadLoading = reactive(new Map<string, boolean>())
const downloadErrors = reactive(new Map<string, string>())

const kindleEnabled = computed(() => settingsStore.isConfigured('kindle'))
const pocketbookEnabled = computed(() => settingsStore.isConfigured('pocketbook'))

onMounted(() => {
  settingsStore.fetchSettings()
})

watch(
  () => selectionStore.count,
  (newCount, oldCount) => {
    if (oldCount === 0 && newCount === 1) {
      drawerOpen.value = true
    }
    if (newCount === 0) {
      drawerOpen.value = false
    }
  },
)

function handleSearch() {
  searchStore.search()
}

function handleToggleSelect(book: Parameters<typeof selectionStore.toggle>[0]) {
  selectionStore.toggle(book)
}

async function handleDownload(md5: string) {
  downloadLoading.set(md5, true)
  downloadErrors.delete(md5)

  try {
    const started = await DownloadService.startDownload(md5)
    queueStore.addOptimisticJob(started.jobId, md5)
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Download failed'
    downloadErrors.set(md5, message)
  } finally {
    downloadLoading.set(md5, false)
  }
}

async function handleDeliver(md5: string, device: string) {
  downloadLoading.set(md5, true)
  downloadErrors.delete(md5)

  try {
    const started = await DownloadService.startDownload(md5)
    queueStore.addOptimisticJob(started.jobId, md5)

    let status = await DownloadService.getDownloadStatus(started.jobId)
    while (status.status !== 'completed' && status.status !== 'failed') {
      await new Promise((resolve) => setTimeout(resolve, 3000))
      status = await DownloadService.getDownloadStatus(started.jobId)
    }

    if (status.status === 'failed') {
      downloadErrors.set(md5, 'Download failed before delivery')
      return
    }

    const { LibraryService, DeliverService } = await import('@/api/generated')
    const library = await LibraryService.getUserLibrary(1, 100)
    const libraryBook = library.items.find((b) => b.bookMd5 === md5)
    if (libraryBook) {
      await DeliverService.deliverBook(libraryBook.id, device as 'kindle' | 'pocketbook')
    } else {
      downloadErrors.set(md5, 'Book not found in library after download')
      return
    }
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Delivery failed'
    downloadErrors.set(md5, message)
  } finally {
    downloadLoading.set(md5, false)
  }
}

function handleDrawerRemove(md5: string) {
  selectionStore.remove(md5)
}

function handleDrawerClear() {
  selectionStore.clear()
  drawerOpen.value = false
}

function handleDownloadAll() {
  for (const book of selectionStore.selectedBooks) {
    handleDownload(book.md5)
  }
}

async function handleKindleAll() {
  for (const book of selectionStore.selectedBooks) {
    await handleDeliver(book.md5, 'kindle')
  }
}

async function handlePocketbookAll() {
  for (const book of selectionStore.selectedBooks) {
    await handleDeliver(book.md5, 'pocketbook')
  }
}

function handleFabClick() {
  drawerOpen.value = true
}

function handleDrawerClose() {
  drawerOpen.value = false
}
</script>

<template>
  <PageHeader title="Search" />

  <SearchToolbar
    :query="searchStore.query"
    :language="searchStore.language"
    :format="searchStore.format"
    :loading="searchStore.loading"
    @update:query="searchStore.query = $event"
    @update:language="searchStore.language = $event"
    @update:format="searchStore.format = $event"
    @search="handleSearch"
  />

  <div class="p-6" :class="{ 'lg:mr-80': drawerOpen }">
    <!-- Error state -->
    <div v-if="searchStore.error">
      <AlertMessage variant="error" :message="searchStore.error" />
      <div class="mt-4">
        <BaseButton
          data-testid="retry-btn"
          variant="primary"
          @click="handleSearch"
        >
          Retry
        </BaseButton>
      </div>
    </div>

    <!-- Loading state -->
    <div v-else-if="searchStore.loading" aria-busy="true">
      <div class="flex flex-col gap-4">
        <BookCardSkeleton v-for="n in 4" :key="n" />
      </div>
    </div>

    <!-- Results state -->
    <div v-else-if="searchStore.hasResults">
      <p class="text-sm text-zinc-400 mb-4" aria-live="polite">
        {{ searchStore.totalResults }} results found
      </p>
      <div class="flex flex-col gap-4">
        <div v-for="book in searchStore.results" :key="book.md5">
          <AlertMessage
            v-if="downloadErrors.get(book.md5)"
            variant="error"
            :message="downloadErrors.get(book.md5)!"
            class="mb-2"
          />
          <BookCard
            :book="book"
            :selected="selectionStore.isSelected(book.md5)"
            :download-loading="downloadLoading.get(book.md5) ?? false"
            :delivery-loading="downloadLoading.get(book.md5) ?? false"
            :kindle-enabled="kindleEnabled"
            :pocketbook-enabled="pocketbookEnabled"
            @toggle-select="handleToggleSelect(book)"
            @download="handleDownload(book.md5)"
            @deliver="handleDeliver(book.md5, $event)"
          />
        </div>
      </div>
    </div>

    <!-- No results state -->
    <div v-else-if="searchStore.isEmpty">
      <EmptyState
        title="No books found"
        :description="`No results for &quot;${searchStore.query}&quot;. Try different keywords or filters.`"
      >
        <template #action>
          <BaseButton variant="ghost" @click="searchStore.resetFilters()">
            Clear filters
          </BaseButton>
        </template>
      </EmptyState>
    </div>

    <!-- Initial empty state -->
    <div v-else>
      <EmptyState
        title="Search for books"
        description="Use the search bar to find your next great read."
      >
        <template #icon>
          <svg
            class="h-12 w-12"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="1.5"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z"
            />
          </svg>
        </template>
      </EmptyState>
    </div>
  </div>

  <SelectionFab
    :count="selectionStore.count"
    @click="handleFabClick"
  />

  <SelectionDrawer
    :books="selectionStore.selectedBooks"
    :open="drawerOpen"
    :kindle-enabled="kindleEnabled"
    :pocketbook-enabled="pocketbookEnabled"
    @close="handleDrawerClose"
    @remove="handleDrawerRemove"
    @clear="handleDrawerClear"
    @download-all="handleDownloadAll"
    @kindle-all="handleKindleAll"
    @pocketbook-all="handlePocketbookAll"
  />
</template>
