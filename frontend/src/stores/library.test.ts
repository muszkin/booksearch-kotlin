import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useLibraryStore } from './library'
import {
  LibraryService,
  DownloadService,
  ConvertService,
  SettingsService,
  CancelablePromise,
} from '@/api/generated'
import type {
  LibraryListResponse,
  DownloadStartedResponse,
  DownloadStatusResponse,
  ConversionStartedResponse,
  ConversionStatusResponse,
} from '@/api/generated'

import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    LibraryService: {
      getUserLibrary: vi.fn(),
      removeFromLibrary: vi.fn(),
    },
    DownloadService: {
      startDownload: vi.fn(),
      getDownloadStatus: vi.fn(),
    },
    ConvertService: {
      startConversion: vi.fn(),
      getConversionStatus: vi.fn(),
    },
    DeliverService: {
      deliverBook: vi.fn(),
      getUserDeliveries: vi.fn(),
      getDeliveriesForBook: vi.fn(),
    },
    SettingsService: {
      getAllSettings: vi.fn(),
    },
  }
})

function resolving<T>(value: T): CancelablePromise<T> {
  return new CancelablePromise((resolve) => resolve(value))
}

function rejecting<T>(error: Error): CancelablePromise<T> {
  return new CancelablePromise((_resolve, reject) => reject(error))
}

const mockLibraryResponse: LibraryListResponse = {
  items: [
    {
      id: 1,
      bookMd5: 'abc123',
      format: 'epub',
      filePath: '/books/test.epub',
      addedAt: '2026-04-10T12:00:00Z',
      title: 'Przestrzen objawienia',
      author: 'Alastair Reynolds',
      language: 'pl',
      fileSize: '1.2 MB',
      detailUrl: '/detail/abc123',
      coverUrl: '/covers/abc123.jpg',
      publisher: 'Wydawnictwo',
      year: '2020',
      description: 'Sci-fi novel',
    },
  ],
  totalItems: 1,
  page: 1,
  pageSize: 20,
  totalPages: 1,
}

describe('useLibraryStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    const store = useLibraryStore()
    store.cleanup()
    vi.clearAllTimers()
    vi.useRealTimers()
  })

  it('fetchLibrary sets books, pagination, loading, and error states', async () => {
    vi.mocked(LibraryService.getUserLibrary).mockReturnValue(
      resolving(mockLibraryResponse),
    )

    const store = useLibraryStore()
    expect(store.loading).toBe(false)

    const fetchPromise = store.fetchLibrary(1)
    expect(store.loading).toBe(true)

    await fetchPromise

    expect(store.loading).toBe(false)
    expect(store.books).toEqual(mockLibraryResponse.items)
    expect(store.pagination.page).toBe(1)
    expect(store.pagination.totalPages).toBe(1)
    expect(store.pagination.totalItems).toBe(1)
    expect(store.error).toBeNull()
  })

  it('fetchLibrary page change replaces books array', async () => {
    const page2Response: LibraryListResponse = {
      items: [
        {
          id: 2,
          bookMd5: 'def456',
          format: 'mobi',
          filePath: null,
          addedAt: '2026-04-11T12:00:00Z',
          title: 'Chasm City',
          author: 'Alastair Reynolds',
          language: 'en',
          fileSize: '2.5 MB',
          detailUrl: '/detail/def456',
          coverUrl: '/covers/def456.jpg',
          publisher: 'Gollancz',
          year: '2001',
          description: 'Sci-fi novel',
        },
      ],
      totalItems: 21,
      page: 2,
      pageSize: 20,
      totalPages: 2,
    }

    vi.mocked(LibraryService.getUserLibrary).mockReturnValue(
      resolving(mockLibraryResponse),
    )

    const store = useLibraryStore()
    await store.fetchLibrary(1)
    expect(store.books[0].id).toBe(1)

    vi.mocked(LibraryService.getUserLibrary).mockReturnValue(
      resolving(page2Response),
    )

    await store.fetchLibrary(2)
    expect(store.books).toHaveLength(1)
    expect(store.books[0].id).toBe(2)
    expect(store.pagination.page).toBe(2)
  })

  it('fetchLibrary sets error on API failure and clears books', async () => {
    vi.mocked(LibraryService.getUserLibrary).mockReturnValue(
      resolving(mockLibraryResponse),
    )

    const store = useLibraryStore()
    await store.fetchLibrary(1)
    expect(store.books).toHaveLength(1)

    vi.mocked(LibraryService.getUserLibrary).mockImplementation(
      () => rejecting(new Error('Network error')),
    )

    await store.fetchLibrary(1)
    expect(store.error).toBe('Network error')
    expect(store.books).toEqual([])
    expect(store.loading).toBe(false)
  })

  it('startDownloadPolling adds entry to activeDownloads and polls until terminal state', async () => {
    const startResponse: DownloadStartedResponse = { jobId: 42, status: 'queued' }
    const statusQueued: DownloadStatusResponse = { jobId: 42, status: 'queued', progress: 0 }
    const statusCompleted: DownloadStatusResponse = { jobId: 42, status: 'completed', progress: 100, filePath: '/books/test.epub' }

    vi.mocked(DownloadService.startDownload).mockImplementation(() => resolving(startResponse))
    vi.mocked(DownloadService.getDownloadStatus)
      .mockImplementationOnce(() => resolving(statusQueued))
      .mockImplementationOnce(() => resolving(statusCompleted))
    vi.mocked(LibraryService.getUserLibrary).mockImplementation(() => resolving(mockLibraryResponse))

    const store = useLibraryStore()
    await store.startDownloadPolling('abc123')

    expect(DownloadService.startDownload).toHaveBeenCalledWith('abc123')
    expect(store.activeDownloads.get('abc123')).toBeDefined()

    // First poll - queued
    vi.advanceTimersByTime(5000)
    await flushPromises()
    await flushPromises()
    expect(DownloadService.getDownloadStatus).toHaveBeenCalledWith(42)

    // Second poll - completed -> should stop
    vi.advanceTimersByTime(5000)
    await flushPromises()
    await flushPromises()
    await flushPromises()

    expect(store.activeDownloads.get('abc123')?.status).toBe('completed')

    // Verify polling stopped
    vi.mocked(DownloadService.getDownloadStatus).mockClear()
    vi.advanceTimersByTime(10000)
    await flushPromises()
    expect(DownloadService.getDownloadStatus).not.toHaveBeenCalled()

    store.cleanup()
  })

  it('startConversionPolling tracks conversion status', async () => {
    const startResponse: ConversionStartedResponse = { jobId: 'conv-1', status: 'queued' }
    const statusConverting: ConversionStatusResponse = {
      jobId: 'conv-1',
      status: 'converting' as ConversionStatusResponse.status,
      sourceFormat: 'epub',
      targetFormat: 'mobi',
    }

    vi.mocked(ConvertService.startConversion).mockReturnValue(resolving(startResponse))
    vi.mocked(ConvertService.getConversionStatus).mockReturnValue(resolving(statusConverting))

    const store = useLibraryStore()
    await store.startConversionPolling(1, 'mobi')

    expect(ConvertService.startConversion).toHaveBeenCalledWith(1, 'mobi')
    expect(store.activeConversions.get(1)).toBeDefined()

    await vi.advanceTimersByTimeAsync(5000)
    expect(ConvertService.getConversionStatus).toHaveBeenCalledWith('conv-1')
  })

  it('fetchDeviceSettings sets kindle/pocketbook booleans from API response', async () => {
    vi.mocked(SettingsService.getAllSettings).mockResolvedValue({
      kindle: {
        host: 'smtp.example.com',
        port: '587',
        username: 'user',
        password: '********',
        fromEmail: 'from@example.com',
        recipientEmail: 'kindle@kindle.com',
      },
    } as Record<string, any>)

    const store = useLibraryStore()
    await store.fetchDeviceSettings()

    expect(store.deviceSettings.kindle).toBe(true)
    expect(store.deviceSettings.pocketbook).toBe(false)
  })

  it('multiple concurrent download polls are tracked independently', async () => {
    const startResponse1: DownloadStartedResponse = { jobId: 10, status: 'queued' }
    const startResponse2: DownloadStartedResponse = { jobId: 20, status: 'queued' }

    vi.mocked(DownloadService.startDownload)
      .mockImplementationOnce(() => resolving(startResponse1))
      .mockImplementationOnce(() => resolving(startResponse2))
    vi.mocked(DownloadService.getDownloadStatus).mockImplementation(() =>
      resolving({ jobId: 10, status: 'downloading', progress: 50 }),
    )

    const store = useLibraryStore()
    await store.startDownloadPolling('book-a')
    await store.startDownloadPolling('book-b')

    expect(store.activeDownloads.get('book-a')).toBeDefined()
    expect(store.activeDownloads.get('book-b')).toBeDefined()
    expect(store.activeDownloads.size).toBe(2)

    store.cleanup()
  })

  it('removeBook rolls back on API failure', async () => {
    vi.mocked(LibraryService.getUserLibrary).mockReturnValue(resolving(mockLibraryResponse))
    vi.mocked(LibraryService.removeFromLibrary).mockImplementation(
      () => rejecting(new Error('Server error')),
    )

    const store = useLibraryStore()
    await store.fetchLibrary(1)
    expect(store.books).toHaveLength(1)

    await expect(store.removeBook(1)).rejects.toThrow('Server error')

    expect(store.books).toHaveLength(1)
    expect(store.books[0].id).toBe(1)
    expect(store.error).toBe('Server error')
  })

  it('removeBook calls API and removes from local state optimistically', async () => {
    vi.mocked(LibraryService.getUserLibrary).mockReturnValue(resolving(mockLibraryResponse))
    vi.mocked(LibraryService.removeFromLibrary).mockReturnValue(resolving({}))

    const store = useLibraryStore()
    await store.fetchLibrary(1)
    expect(store.books).toHaveLength(1)

    await store.removeBook(1)

    expect(store.books).toHaveLength(0)
    expect(LibraryService.removeFromLibrary).toHaveBeenCalledWith(1)
  })
})
