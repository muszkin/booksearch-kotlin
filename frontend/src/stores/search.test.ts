import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSearchStore } from './search'
import { SearchService, CancelablePromise, BookResult } from '@/api/generated'
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

const mockBook: BookResult = {
  md5: 'abc123',
  title: 'Przestrzen objawienia',
  author: 'Alastair Reynolds',
  language: 'pl',
  format: 'epub',
  fileSize: '1.2 MB',
  detailUrl: '/detail/abc123',
  coverUrl: '/covers/abc123.jpg',
  publisher: 'Wydawnictwo',
  year: '2020',
  description: 'Sci-fi novel',
  matchType: BookResult.matchType.NONE,
  ownedFormats: [],
}

const mockSearchResponse: SearchResponse = {
  results: [mockBook],
  totalResults: 1,
  query: 'Przestrzen',
}

function createResolvingPromise<T>(value: T): CancelablePromise<T> {
  return new CancelablePromise((resolve) => resolve(value))
}

function createRejectingPromise<T>(error: Error): CancelablePromise<T> {
  return new CancelablePromise((_resolve, reject) => reject(error))
}

describe('useSearchStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('calls SearchService.searchBooks with correct params', async () => {
    vi.mocked(SearchService.searchBooks).mockReturnValue(
      createResolvingPromise(mockSearchResponse),
    )

    const store = useSearchStore()
    store.query = 'Przestrzen'
    store.language = 'pl'
    store.format = 'epub'

    await store.search()

    expect(SearchService.searchBooks).toHaveBeenCalledWith('Przestrzen', 'pl', 'epub')
  })

  it('sets loading true during API call and false after', async () => {
    let resolvePromise: (value: SearchResponse) => void
    vi.mocked(SearchService.searchBooks).mockReturnValue(
      new CancelablePromise((resolve) => {
        resolvePromise = resolve
      }),
    )

    const store = useSearchStore()
    store.query = 'test'

    const searchPromise = store.search()
    expect(store.loading).toBe(true)

    resolvePromise!(mockSearchResponse)
    await searchPromise

    expect(store.loading).toBe(false)
  })

  it('stores results from SearchResponse on success', async () => {
    vi.mocked(SearchService.searchBooks).mockReturnValue(
      createResolvingPromise(mockSearchResponse),
    )

    const store = useSearchStore()
    store.query = 'Przestrzen'
    await store.search()

    expect(store.results).toEqual([mockBook])
    expect(store.totalResults).toBe(1)
    expect(store.hasResults).toBe(true)
    expect(store.error).toBeNull()
  })

  it('sets error string on API failure and clears results', async () => {
    vi.mocked(SearchService.searchBooks).mockReturnValue(
      createResolvingPromise(mockSearchResponse),
    )

    const store = useSearchStore()
    store.query = 'Przestrzen'
    await store.search()
    expect(store.results).toHaveLength(1)

    vi.mocked(SearchService.searchBooks).mockReturnValue(
      createRejectingPromise(new Error('Network error')),
    )

    store.query = 'failing query'
    await store.search()

    expect(store.error).toBe('Network error')
    expect(store.results).toEqual([])
    expect(store.loading).toBe(false)
  })
})
