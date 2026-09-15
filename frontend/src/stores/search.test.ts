import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSearchStore } from './search'
import { SearchService, CancelablePromise, BookResult, SearchJobStatusResponse } from '@/api/generated'
import type { SearchStartedResponse } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    SearchService: {
      submitSearch: vi.fn(),
      getSearchStatus: vi.fn(),
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

const startedJob: SearchStartedResponse = { jobId: 7, status: 'queued' }

function jobStatus(overrides: Partial<SearchJobStatusResponse>): SearchJobStatusResponse {
  return {
    jobId: 7,
    query: 'Przestrzen',
    status: SearchJobStatusResponse.status.QUEUED,
    results: [],
    totalResults: 0,
    ...overrides,
  }
}

function resolved<T>(value: T): CancelablePromise<T> {
  return new CancelablePromise((resolve) => resolve(value))
}

describe('useSearchStore async search', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('submits the query and exposes results once the job completes', async () => {
    vi.mocked(SearchService.submitSearch).mockReturnValue(resolved(startedJob))
    vi.mocked(SearchService.getSearchStatus).mockReturnValue(
      resolved(
        jobStatus({
          status: SearchJobStatusResponse.status.COMPLETED,
          results: [mockBook],
          totalResults: 1,
        }),
      ),
    )
    const store = useSearchStore()
    store.query = 'Przestrzen'

    await store.search()

    expect(SearchService.submitSearch).toHaveBeenCalledWith('Przestrzen', 'pl', 'epub')
    expect(SearchService.getSearchStatus).toHaveBeenCalledWith(7)
    expect(store.results).toEqual([mockBook])
    expect(store.totalResults).toBe(1)
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('surfaces the backend error when the job fails', async () => {
    vi.mocked(SearchService.submitSearch).mockReturnValue(resolved(startedJob))
    vi.mocked(SearchService.getSearchStatus).mockReturnValue(
      resolved(
        jobStatus({
          status: SearchJobStatusResponse.status.FAILED,
          error: 'No working mirror available',
        }),
      ),
    )
    const store = useSearchStore()
    store.query = 'Przestrzen'

    await store.search()

    expect(store.error).toBe('No working mirror available')
    expect(store.results).toEqual([])
    expect(store.loading).toBe(false)
  })

  it('keeps polling while the job is still scraping', async () => {
    vi.useFakeTimers()
    vi.mocked(SearchService.submitSearch).mockReturnValue(resolved(startedJob))
    vi.mocked(SearchService.getSearchStatus)
      .mockReturnValueOnce(resolved(jobStatus({ status: SearchJobStatusResponse.status.SCRAPING })))
      .mockReturnValueOnce(
        resolved(
          jobStatus({
            status: SearchJobStatusResponse.status.COMPLETED,
            results: [mockBook],
            totalResults: 1,
          }),
        ),
      )
    const store = useSearchStore()
    store.query = 'Przestrzen'

    const pending = store.search()
    await vi.advanceTimersByTimeAsync(1500)
    await pending

    expect(SearchService.getSearchStatus).toHaveBeenCalledTimes(2)
    expect(store.results).toEqual([mockBook])
  })

  it('gives up after the polling timeout instead of spinning forever', async () => {
    vi.useFakeTimers()
    vi.mocked(SearchService.submitSearch).mockReturnValue(resolved(startedJob))
    vi.mocked(SearchService.getSearchStatus).mockReturnValue(
      resolved(jobStatus({ status: SearchJobStatusResponse.status.SCRAPING })),
    )
    const store = useSearchStore()
    store.query = 'Przestrzen'

    const pending = store.search()
    await vi.advanceTimersByTimeAsync(5 * 60 * 1000)
    await pending

    expect(store.error).toBe('Search timed out. Try a narrower query.')
    expect(store.loading).toBe(false)
  })
})
