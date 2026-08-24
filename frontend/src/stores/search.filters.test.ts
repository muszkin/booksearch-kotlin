import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSearchStore } from './search'
import { SearchService, CancelablePromise, BookResult, SearchJobStatusResponse } from '@/api/generated'
import type { SearchStartedResponse } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    SearchService: { submitSearch: vi.fn(), getSearchStatus: vi.fn() },
  }
})

function book(overrides: Partial<BookResult>): BookResult {
  return {
    md5: 'md5-' + Math.random().toString(36).slice(2),
    title: 'Title',
    author: 'Lem',
    language: 'Polish [pl]',
    format: 'epub',
    fileSize: '1MB',
    detailUrl: '/d',
    coverUrl: '/c',
    publisher: 'WL',
    year: '1987',
    description: '',
    matchType: BookResult.matchType.NONE,
    ownedFormats: [],
    ...overrides,
  }
}

function resolved<T>(value: T): CancelablePromise<T> {
  return new CancelablePromise((resolve) => resolve(value))
}

async function storeWith(results: BookResult[]) {
  vi.mocked(SearchService.submitSearch).mockReturnValue(
    resolved<SearchStartedResponse>({ jobId: 1, status: 'queued' }),
  )
  vi.mocked(SearchService.getSearchStatus).mockReturnValue(
    resolved({
      jobId: 1,
      query: 'lem',
      status: SearchJobStatusResponse.status.COMPLETED,
      results,
      totalResults: results.length,
    }),
  )
  const store = useSearchStore()
  store.query = 'lem'
  await store.search()
  return store
}

describe('search facets', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('counts each distinct author across the results', async () => {
    const store = await storeWith([
      book({ author: 'Lem' }),
      book({ author: 'Lem' }),
      book({ author: 'Dick' }),
    ])

    expect(store.facets.authors).toEqual([
      { value: 'Lem', count: 2 },
      { value: 'Dick', count: 1 },
    ])
  })

  it('groups entries with no author under Unknown so they stay filterable', async () => {
    const store = await storeWith([book({ author: '' }), book({ author: 'Lem' })])

    expect(store.facets.authors).toContainEqual({ value: 'Unknown', count: 1 })
  })

  it('derives formats from the results rather than a fixed list', async () => {
    const store = await storeWith([book({ format: 'azw3' }), book({ format: 'epub' })])

    expect(store.facets.formats.map((f) => f.value).sort()).toEqual(['azw3', 'epub'])
  })
})

describe('hiding results', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('removes results whose author is hidden', async () => {
    const store = await storeWith([book({ author: 'Lem' }), book({ author: 'Dick' })])

    store.hideAuthor('Dick')

    expect(store.visibleResults.map((b) => b.author)).toEqual(['Lem'])
  })

  it('removes results whose publisher is hidden', async () => {
    const store = await storeWith([book({ publisher: 'WL' }), book({ publisher: 'Mag' })])

    store.hidePublisher('Mag')

    expect(store.visibleResults.map((b) => b.publisher)).toEqual(['WL'])
  })

  it('removes results whose format is hidden', async () => {
    const store = await storeWith([book({ format: 'epub' }), book({ format: 'pdf' })])

    store.hideFormat('pdf')

    expect(store.visibleResults.map((b) => b.format)).toEqual(['epub'])
  })

  it('removes results whose language is hidden', async () => {
    const store = await storeWith([
      book({ language: 'Polish [pl]' }),
      book({ language: 'English [en]' }),
    ])

    store.hideLanguage('English [en]')

    expect(store.visibleResults.map((b) => b.language)).toEqual(['Polish [pl]'])
  })

  it('reports how many results survive the active filters', async () => {
    const store = await storeWith([book({ author: 'Lem' }), book({ author: 'Dick' })])

    store.hideAuthor('Dick')

    expect(store.visibleCount).toBe(1)
    expect(store.totalResults).toBe(2)
  })

  it('forgets hidden values when a new search runs', async () => {
    const store = await storeWith([book({ author: 'Lem' }), book({ author: 'Dick' })])
    store.hideAuthor('Dick')

    await store.search()

    expect(store.visibleResults).toHaveLength(2)
  })
})

describe('sorting by release year', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('keeps the service order when no sort is chosen', async () => {
    const store = await storeWith([book({ year: '2016' }), book({ year: '1961' })])

    expect(store.visibleResults.map((b) => b.year)).toEqual(['2016', '1961'])
  })

  it('sorts ascending by year', async () => {
    const store = await storeWith([
      book({ year: '2016' }),
      book({ year: '1961' }),
      book({ year: '1987' }),
    ])

    store.sortDirection = 'asc'

    expect(store.visibleResults.map((b) => b.year)).toEqual(['1961', '1987', '2016'])
  })

  it('sorts descending by year', async () => {
    const store = await storeWith([book({ year: '1961' }), book({ year: '2016' })])

    store.sortDirection = 'desc'

    expect(store.visibleResults.map((b) => b.year)).toEqual(['2016', '1961'])
  })

  it('puts entries without a year last when sorting ascending', async () => {
    const store = await storeWith([book({ year: '' }), book({ year: '1987' })])

    store.sortDirection = 'asc'

    expect(store.visibleResults.map((b) => b.year)).toEqual(['1987', ''])
  })

  it('puts entries without a year last when sorting descending too', async () => {
    const store = await storeWith([book({ year: '' }), book({ year: '1987' })])

    store.sortDirection = 'desc'

    expect(store.visibleResults.map((b) => b.year)).toEqual(['1987', ''])
  })
})
