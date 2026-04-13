<script setup lang="ts">
import { ref, watch } from 'vue'
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
import { DownloadService, LibraryService } from '@/api/generated'

const searchStore = useSearchStore()
const selectionStore = useSelectionStore()

const drawerOpen = ref(false)

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

function handleDownload(md5: string) {
  DownloadService.startDownload(md5)
}

function handleAddToLibrary(md5: string, format: string) {
  LibraryService.addToLibrary({ bookMd5: md5, format })
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
    DownloadService.startDownload(book.md5)
  }
}

function handleAddAllToLibrary() {
  for (const book of selectionStore.selectedBooks) {
    LibraryService.addToLibrary({ bookMd5: book.md5, format: book.format })
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
        <BookCard
          v-for="book in searchStore.results"
          :key="book.md5"
          :book="book"
          :selected="selectionStore.isSelected(book.md5)"
          @toggle-select="handleToggleSelect(book)"
          @download="handleDownload(book.md5)"
          @add-to-library="handleAddToLibrary(book.md5, book.format)"
        />
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
    @close="handleDrawerClose"
    @remove="handleDrawerRemove"
    @clear="handleDrawerClear"
    @download-all="handleDownloadAll"
    @add-all-to-library="handleAddAllToLibrary"
  />
</template>
