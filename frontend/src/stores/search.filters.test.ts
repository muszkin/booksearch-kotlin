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
      { value: 'Dick', count: 1 },
      { value: 'Lem', count: 2 },
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

describe('facet value normalisation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('splits a semicolon separated author list into individual names', async () => {
    const store = await storeWith([
      book({ author: 'Ross Macdonald; Włodzimierz Grabowski; Dorota Wieczorek' }),
    ])

    expect(store.facets.authors.map((a) => a.value).sort()).toEqual([
      'Dorota Wieczorek',
      'Ross Macdonald',
      'Włodzimierz Grabowski',
    ])
  })

  it('counts an author once per book they appear in', async () => {
    const store = await storeWith([
      book({ author: 'Stefan Gemmel; Paweł Wieczorek' }),
      book({ author: 'Paweł Wieczorek' }),
    ])

    expect(store.facets.authors).toContainEqual({ value: 'Paweł Wieczorek', count: 2 })
    expect(store.facets.authors).toContainEqual({ value: 'Stefan Gemmel', count: 1 })
  })

  it('keeps a book while any of its authors is still visible', async () => {
    const store = await storeWith([book({ author: 'Stefan Gemmel; Paweł Wieczorek' })])

    store.hideAuthor('Stefan Gemmel')

    expect(store.visibleResults).toHaveLength(1)
  })

  it('drops a book once every one of its authors is hidden', async () => {
    const store = await storeWith([book({ author: 'Stefan Gemmel; Paweł Wieczorek' })])

    store.hideAuthor('Stefan Gemmel')
    store.hideAuthor('Paweł Wieczorek')

    expect(store.visibleResults).toHaveLength(0)
  })

  it('strips the trailing year from a publisher so editions group together', async () => {
    const store = await storeWith([
      book({ publisher: 'AMBER, Wydawnictwo, 2011' }),
      book({ publisher: 'AMBER, Wydawnictwo, 2013' }),
    ])

    expect(store.facets.publishers).toEqual([{ value: 'AMBER, Wydawnictwo', count: 2 }])
  })

  it('hides every edition of a publisher at once', async () => {
    const store = await storeWith([
      book({ publisher: 'AMBER, Wydawnictwo, 2011' }),
      book({ publisher: 'Solaris, PS, 2014' }),
    ])

    store.hidePublisher('AMBER, Wydawnictwo')

    expect(store.visibleResults.map((b) => b.publisher)).toEqual(['Solaris, PS, 2014'])
  })

  it('leaves a publisher name that does not end in a year alone', async () => {
    const store = await storeWith([book({ publisher: 'Wydawnictwo Literackie' })])

    expect(store.facets.publishers).toEqual([{ value: 'Wydawnictwo Literackie', count: 1 }])
  })
})

describe('facet ordering', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('orders facet values alphabetically rather than by count', async () => {
    const store = await storeWith([
      book({ author: 'Zelazny' }),
      book({ author: 'Zelazny' }),
      book({ author: 'Asimov' }),
    ])

    expect(store.facets.authors.map((a) => a.value)).toEqual(['Asimov', 'Zelazny'])
  })

  it('collates Polish letters in their alphabetical place', async () => {
    const store = await storeWith([
      book({ author: 'Zabinski' }),
      book({ author: 'Łuczak' }),
      book({ author: 'Adamski' }),
    ])

    expect(store.facets.authors.map((a) => a.value)).toEqual(['Adamski', 'Łuczak', 'Zabinski'])
  })
})

describe('bulk hiding', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('hides every named author at once', async () => {
    const store = await storeWith([
      book({ author: 'Lem' }),
      book({ author: 'Dick' }),
      book({ author: 'Herbert' }),
    ])

    store.setAuthorsHidden(['Lem', 'Dick'], true)

    expect(store.visibleResults.map((b) => b.author)).toEqual(['Herbert'])
  })

  it('restores every named author at once', async () => {
    const store = await storeWith([book({ author: 'Lem' }), book({ author: 'Dick' })])
    store.setAuthorsHidden(['Lem', 'Dick'], true)

    store.setAuthorsHidden(['Lem', 'Dick'], false)

    expect(store.visibleResults).toHaveLength(2)
  })

  it('leaves authors outside the named set untouched', async () => {
    const store = await storeWith([book({ author: 'Lem' }), book({ author: 'Dick' })])
    store.setAuthorsHidden(['Lem'], true)

    store.setAuthorsHidden(['Dick'], true)

    expect(store.hiddenAuthors.has('Lem')).toBe(true)
  })

  it('hides publishers, formats and languages in bulk too', async () => {
    const store = await storeWith([
      book({ publisher: 'WL', format: 'epub', language: 'Polish [pl]' }),
      book({ publisher: 'Mag', format: 'pdf', language: 'English [en]' }),
    ])

    store.setPublishersHidden(['Mag'], true)
    store.setFormatsHidden(['pdf'], true)
    store.setLanguagesHidden(['English [en]'], true)

    expect(store.visibleResults.map((b) => b.publisher)).toEqual(['WL'])
  })
})
