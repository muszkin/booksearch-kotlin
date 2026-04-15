import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useDownloadQueueStore } from './download-queue'
import { useLibraryStore } from './library'
import { DownloadService, DeliverService, CancelablePromise } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    DownloadService: {
      getDownloadJobs: vi.fn(),
      cancelDownloadJob: vi.fn(),
      startDownload: vi.fn(),
      getDownloadStatus: vi.fn(),
    },
    LibraryService: {
      getUserLibrary: vi.fn().mockResolvedValue({
        items: [],
        page: 1,
        pageSize: 20,
        totalPages: 0,
        totalItems: 0,
      }),
      removeFromLibrary: vi.fn(),
      batchDownload: vi.fn(),
    },
    ConvertService: {
      startConversion: vi.fn(),
      getConversionStatus: vi.fn(),
    },
    DeliverService: {
      deliverBook: vi.fn(),
      getUserDeliveries: vi.fn().mockResolvedValue([]),
    },
    SettingsService: {
      getAllSettings: vi.fn().mockResolvedValue({}),
    },
  }
})

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    interceptors: {
      request: { use: vi.fn(), eject: vi.fn() },
      response: { use: vi.fn(), eject: vi.fn() },
    },
    defaults: { headers: { common: {} } },
  },
}))

function resolving<T>(value: T): CancelablePromise<T> {
  return new CancelablePromise((resolve) => resolve(value))
}

function rejecting<T>(error: Error): CancelablePromise<T> {
  return new CancelablePromise((_, reject) => reject(error))
}

describe('Download Queue Store - tab switching resets page and fetches', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('resets page to 1 and fetches with new status when tab changes', async () => {
    vi.mocked(DownloadService.getDownloadJobs).mockReturnValue(
      resolving({ items: [], totalCount: 0 }),
    )

    const store = useDownloadQueueStore()
    store.page = 3

    await store.setTab('completed')

    expect(store.currentTab).toBe('completed')
    expect(store.page).toBe(1)
    expect(DownloadService.getDownloadJobs).toHaveBeenCalledWith('completed', 1, 20)
  })
})

describe('Library Store - delivery workflow sets error on failure', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('sets error when delivery fails and re-throws', async () => {
    vi.mocked(DeliverService.deliverBook).mockRejectedValue(new Error('SMTP not configured'))

    const store = useLibraryStore()

    await expect(store.deliverBook(1, 'kindle')).rejects.toThrow('SMTP not configured')
    expect(store.error).toBe('SMTP not configured')
  })
})

describe('Library Store - download polling tracks active downloads', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('marks book as downloading after startDownloadPolling', async () => {
    vi.mocked(DownloadService.startDownload).mockResolvedValue({ jobId: 42, status: 'queued' })

    const store = useLibraryStore()
    await store.startDownloadPolling('test-md5')

    expect(store.isDownloading('test-md5')).toBe(true)
    expect(store.activeDownloads.get('test-md5')?.jobId).toBe(42)

    store.cleanup()
  })
})
