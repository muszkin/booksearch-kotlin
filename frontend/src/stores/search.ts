import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { SearchService } from '@/api/generated'
import type { BookResult } from '@/api/generated'

export const useSearchStore = defineStore('search', () => {
  const query = ref('')
  const language = ref('pl')
  const format = ref('epub')
  const results = ref<BookResult[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const totalResults = ref(0)

  const hasResults = computed(() => results.value.length > 0)
  const isEmpty = computed(() => query.value.length > 0 && !hasResults.value && !loading.value)

  async function search() {
    if (!query.value.trim()) return

    loading.value = true
    error.value = null

    try {
      const response = await SearchService.searchBooks(query.value, language.value, format.value)
      results.value = response.results
      totalResults.value = response.totalResults
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Search failed'
      results.value = []
      totalResults.value = 0
    } finally {
      loading.value = false
    }
  }

  function resetFilters() {
    language.value = 'pl'
    format.value = 'epub'
    query.value = ''
  }

  return {
    query,
    language,
    format,
    results,
    loading,
    error,
    totalResults,
    hasResults,
    isEmpty,
    search,
    resetFilters,
  }
})
