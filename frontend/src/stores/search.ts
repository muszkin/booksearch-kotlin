import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { SearchService, SearchJobStatusResponse } from '@/api/generated'
import type { BookResult } from '@/api/generated'

export type SortDirection = 'none' | 'asc' | 'desc'
export type SearchLanguage = 'pl' | 'en' | 'de' | 'any'
export type SearchFormat = 'epub' | 'mobi' | 'pdf' | 'any'

export interface Facet {
  value: string
  count: number
}

const UNKNOWN_VALUE = 'Unknown'
/** Anna's Archive packs every contributor into one field, separated by semicolons. */
const AUTHOR_SEPARATOR = /\s*;\s*/
/** Publisher strings carry the edition year: "AMBER, Wydawnictwo, 2011". */
const TRAILING_YEAR = /,\s*(1[0-9]|20)\d{2}\s*$/
/** Explicit collation: the default locale can sort Polish diacritics after Z. */
const COLLATOR = new Intl.Collator('pl', { sensitivity: 'base', numeric: true })
const POLL_INTERVAL_MS = 1500
const POLL_TIMEOUT_MS = 5 * 60 * 1000
const TIMEOUT_MESSAGE = 'Search timed out. Try a narrower query.'

export const useSearchStore = defineStore('search', () => {
  const query = ref('')
  const language = ref<SearchLanguage>('pl')
  const format = ref<SearchFormat>('epub')
  const results = ref<BookResult[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const totalResults = ref(0)

  const hiddenAuthors = ref<Set<string>>(new Set())
  const hiddenPublishers = ref<Set<string>>(new Set())
  const hiddenFormats = ref<Set<string>>(new Set())
  const hiddenLanguages = ref<Set<string>>(new Set())
  const sortDirection = ref<SortDirection>('none')

  const facets = computed(() => ({
    authors: facetForMany(authorsOf),
    publishers: facetFor(publisherOf),
    formats: facetFor((book) => book.format),
    languages: facetFor((book) => book.language),
  }))

  const visibleResults = computed(() => {
    const kept = results.value.filter(
      (book) =>
        // A co-authored book survives while any one of its authors is still visible.
        authorsOf(book).some((author) => !hiddenAuthors.value.has(author)) &&
        !hiddenPublishers.value.has(publisherOf(book)) &&
        !hiddenFormats.value.has(labelOf(book.format)) &&
        !hiddenLanguages.value.has(labelOf(book.language)),
    )
    return sortDirection.value === 'none' ? kept : sortByYear(kept, sortDirection.value)
  })

  const visibleCount = computed(() => visibleResults.value.length)

  const hasActiveFilters = computed(
    () =>
      hiddenAuthors.value.size > 0 ||
      hiddenPublishers.value.size > 0 ||
      hiddenFormats.value.size > 0 ||
      hiddenLanguages.value.size > 0,
  )

  const hasResults = computed(() => results.value.length > 0)
  const isEmpty = computed(() => query.value.length > 0 && !hasResults.value && !loading.value)

  async function search() {
    if (!query.value.trim()) return

    loading.value = true
    error.value = null
    results.value = []
    totalResults.value = 0
    clearHidden()

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

  function facetFor(pick: (book: BookResult) => string): Facet[] {
    return facetForMany((book) => [pick(book)])
  }

  /** Counts each value once per book, so a co-author is not counted twice for one title. */
  function facetForMany(pick: (book: BookResult) => string[]): Facet[] {
    const counts = new Map<string, number>()
    for (const book of results.value) {
      for (const value of new Set(pick(book))) {
        const label = labelOf(value)
        counts.set(label, (counts.get(label) ?? 0) + 1)
      }
    }
    return [...counts.entries()]
      .map(([value, count]) => ({ value, count }))
      .sort((a, b) => COLLATOR.compare(a.value, b.value))
  }

  function authorsOf(book: BookResult): string[] {
    const names = book.author
      .split(AUTHOR_SEPARATOR)
      .map((name) => name.trim())
      .filter((name) => name !== '')
    return names.length === 0 ? [UNKNOWN_VALUE] : names
  }

  function publisherOf(book: BookResult): string {
    return labelOf(book.publisher.replace(TRAILING_YEAR, ''))
  }

  function labelOf(value: string): string {
    return value.trim() === '' ? UNKNOWN_VALUE : value.trim()
  }

  /** Entries without a year sort last in both directions: they have no date to order by. */
  function sortByYear(books: BookResult[], direction: SortDirection): BookResult[] {
    return [...books].sort((a, b) => {
      const yearA = parseYear(a.year)
      const yearB = parseYear(b.year)
      if (yearA === null && yearB === null) return 0
      if (yearA === null) return 1
      if (yearB === null) return -1
      return direction === 'asc' ? yearA - yearB : yearB - yearA
    })
  }

  function parseYear(year: string): number | null {
    const parsed = Number.parseInt(year, 10)
    return Number.isNaN(parsed) ? null : parsed
  }

  function toggleIn(set: Set<string>, value: string) {
    if (set.has(value)) {
      set.delete(value)
    } else {
      set.add(value)
    }
  }

  function hideAuthor(value: string) {
    toggleIn(hiddenAuthors.value, value)
    hiddenAuthors.value = new Set(hiddenAuthors.value)
  }

  function hidePublisher(value: string) {
    toggleIn(hiddenPublishers.value, value)
    hiddenPublishers.value = new Set(hiddenPublishers.value)
  }

  function hideFormat(value: string) {
    toggleIn(hiddenFormats.value, value)
    hiddenFormats.value = new Set(hiddenFormats.value)
  }

  function hideLanguage(value: string) {
    toggleIn(hiddenLanguages.value, value)
    hiddenLanguages.value = new Set(hiddenLanguages.value)
  }

  function setManyHidden(current: Set<string>, values: string[], hidden: boolean): Set<string> {
    const next = new Set(current)
    for (const value of values) {
      if (hidden) {
        next.add(value)
      } else {
        next.delete(value)
      }
    }
    return next
  }

  function setAuthorsHidden(values: string[], hidden: boolean) {
    hiddenAuthors.value = setManyHidden(hiddenAuthors.value, values, hidden)
  }

  function setPublishersHidden(values: string[], hidden: boolean) {
    hiddenPublishers.value = setManyHidden(hiddenPublishers.value, values, hidden)
  }

  function setFormatsHidden(values: string[], hidden: boolean) {
    hiddenFormats.value = setManyHidden(hiddenFormats.value, values, hidden)
  }

  function setLanguagesHidden(values: string[], hidden: boolean) {
    hiddenLanguages.value = setManyHidden(hiddenLanguages.value, values, hidden)
  }

  /** A new query returns a different set of authors, so stale hides would bury random results. */
  function clearHidden() {
    hiddenAuthors.value = new Set()
    hiddenPublishers.value = new Set()
    hiddenFormats.value = new Set()
    hiddenLanguages.value = new Set()
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
    facets,
    visibleResults,
    visibleCount,
    hasActiveFilters,
    sortDirection,
    hiddenAuthors,
    hiddenPublishers,
    hiddenFormats,
    hiddenLanguages,
    hideAuthor,
    hidePublisher,
    hideFormat,
    hideLanguage,
    setAuthorsHidden,
    setPublishersHidden,
    setFormatsHidden,
    setLanguagesHidden,
    clearHidden,
  }
})
