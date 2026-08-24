import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { SearchService, SearchJobStatusResponse } from '@/api/generated'
import type { BookResult } from '@/api/generated'

const POLL_INTERVAL_MS = 1500
const POLL_TIMEOUT_MS = 5 * 60 * 1000
const TIMEOUT_MESSAGE = 'Search timed out. Try a narrower query.'

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
    results.value = []
    totalResults.value = 0

    try {
      const job = await SearchService.submitSearch(query.value, language.value, format.value)
      const completed = await pollUntilTerminal(job.jobId)
      results.value = completed.results
      totalResults.value = completed.totalResults
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Search failed'
      results.value = []
      totalResults.value = 0
    } finally {
      loading.value = false
    }
  }

  async function pollUntilTerminal(jobId: number): Promise<SearchJobStatusResponse> {
    const deadline = Date.now() + POLL_TIMEOUT_MS

    while (Date.now() < deadline) {
      const job = await SearchService.getSearchStatus(jobId)

      if (job.status === SearchJobStatusResponse.status.COMPLETED) {
        return job
      }
      if (job.status === SearchJobStatusResponse.status.FAILED) {
        throw new Error(job.error ?? 'Search failed')
      }

      await sleep(POLL_INTERVAL_MS)
    }

    throw new Error(TIMEOUT_MESSAGE)
  }

  function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms))
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
