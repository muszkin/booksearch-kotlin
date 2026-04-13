import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSearchStore } from './search'
import { SearchService, CancelablePromise } from '@/api/generated'
import type { SearchResponse } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    SearchService: {
      searchBooks: vi.fn(),
    },
  }
})

describe('useSearchStore gap coverage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('search() does nothing when query is empty or whitespace only', async () => {
    const store = useSearchStore()

    store.query = ''
    await store.search()
    expect(SearchService.searchBooks).not.toHaveBeenCalled()

    store.query = '   '
    await store.search()
    expect(SearchService.searchBooks).not.toHaveBeenCalled()

    expect(store.loading).toBe(false)
  })

  it('resetFilters restores defaults for language, format, and query', async () => {
    const store = useSearchStore()

    store.query = 'test'
    store.language = 'en'
    store.format = 'pdf'

    store.resetFilters()

    expect(store.query).toBe('')
    expect(store.language).toBe('pl')
    expect(store.format).toBe('epub')
  })

  it('isEmpty returns true when query is set but results are empty and not loading', async () => {
    const emptyResponse: SearchResponse = { results: [], totalResults: 0, query: 'xyz' }
    vi.mocked(SearchService.searchBooks).mockReturnValue(
      new CancelablePromise((resolve) => resolve(emptyResponse)),
    )

    const store = useSearchStore()
    expect(store.isEmpty).toBe(false)

    store.query = 'xyz'
    await store.search()

    expect(store.isEmpty).toBe(true)
    expect(store.hasResults).toBe(false)
    expect(store.loading).toBe(false)
  })
})
