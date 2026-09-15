import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import apiClient from '@/api/client'
import {
  LibraryService,
  DownloadService,
  ConvertService,
  DeliverService,
  SettingsService,
  SearchService,
} from '@/api/generated'
import type {
  LibraryBook,
  DownloadStatusResponse,
  ConversionStatusResponse,
  DeliveryRecord,
} from '@/api/generated'

const POLL_INTERVAL_MS = 5000
/** Each lookup can take seconds; a handful at a time keeps a full page from crawling. */
const DESCRIPTION_LOOKUP_CONCURRENCY = 3
const TERMINAL_DOWNLOAD_STATUSES = ['completed', 'failed', 'cancelled']
const TERMINAL_CONVERSION_STATUSES = ['completed', 'failed']

export const useLibraryStore = defineStore('library', () => {
  const books = ref<LibraryBook[]>([])
  const pagination = ref({ page: 1, pageSize: 20, totalPages: 0, totalItems: 0 })
  const activeDownloads = ref(new Map<string, DownloadStatusResponse>())
  const activeConversions = ref(new Map<number, ConversionStatusResponse>())
  const deliveries = ref(new Map<string, DeliveryRecord[]>())
  const deviceSettings = ref({ kindle: false, pocketbook: false })
  const canRegenerate = ref(false)
  const descriptionLoading = ref(new Set<string>())
  const descriptionsWithoutText = ref(new Set<string>())
  const loading = ref(false)
  const error = ref<string | null>(null)

  const downloadIntervals = new Map<string, ReturnType<typeof setInterval>>()
  const conversionIntervals = new Map<number, ReturnType<typeof setInterval>>()

  const hasBooks = computed(() => books.value.length > 0)
  const isEmpty = computed(() => !hasBooks.value && !loading.value && !error.value)

  function isDownloading(bookMd5: string): boolean {
    const status = activeDownloads.value.get(bookMd5)
    return !!status && !TERMINAL_DOWNLOAD_STATUSES.includes(status.status)
  }

  function isConverting(libraryId: number): boolean {
    const status = activeConversions.value.get(libraryId)
    return !!status && !TERMINAL_CONVERSION_STATUSES.includes(status.status)
  }

  function getDeliveries(bookMd5: string): DeliveryRecord[] {
    return deliveries.value.get(bookMd5) ?? []
  }

  function isDescriptionLoading(bookMd5: string): boolean {
    return descriptionLoading.value.has(bookMd5)
  }

  function isDescriptionMissing(bookMd5: string): boolean {
    return descriptionsWithoutText.value.has(bookMd5)
  }

  function markDescriptionLoading(bookMd5: string, isLoading: boolean) {
    const updated = new Set(descriptionLoading.value)
    if (isLoading) {
      updated.add(bookMd5)
    } else {
      updated.delete(bookMd5)
    }
    descriptionLoading.value = updated
  }

  function markDescriptionMissing(bookMd5: string, isMissing: boolean) {
    const updated = new Set(descriptionsWithoutText.value)
    if (isMissing) {
      updated.add(bookMd5)
    } else {
      updated.delete(bookMd5)
    }
    descriptionsWithoutText.value = updated
  }

  /** The same book can sit in the library in several formats; all of them show the text. */
  function applyDescription(bookMd5: string, description: string, source: string) {
    books.value = books.value.map((book) =>
      book.bookMd5 === bookMd5 ? { ...book, description, descriptionSource: source } : book,
    )
  }

  async function resolveDescription(bookMd5: string) {
    markDescriptionLoading(bookMd5, true)
    try {
      const resolved = await SearchService.getBookDescription(bookMd5)
      applyDescription(bookMd5, resolved.description, resolved.source)
      markDescriptionMissing(bookMd5, false)
    } catch {
      // A book nothing knows about is an ordinary outcome, not an error worth shouting about.
      markDescriptionMissing(bookMd5, true)
    } finally {
      markDescriptionLoading(bookMd5, false)
    }
  }

  /**
   * Looks up the books the library holds no description for. The backend remembers a
   * fruitless lookup, so revisiting the page does not repeat the expensive part.
   */
  async function resolveMissingDescriptions() {
    const pending = [
      ...new Set(
        books.value
          .filter((book) => !book.description)
          .map((book) => book.bookMd5)
          .filter((md5) => !isDescriptionLoading(md5) && !isDescriptionMissing(md5)),
      ),
    ]
    if (pending.length === 0) return

    for (const md5 of pending) markDescriptionLoading(md5, true)

    const queue = [...pending]
    const workers = Array.from(
      { length: Math.min(DESCRIPTION_LOOKUP_CONCURRENCY, queue.length) },
      async () => {
        let next = queue.shift()
        while (next !== undefined) {
          await resolveDescription(next)
          next = queue.shift()
        }
      },
    )

    await Promise.all(workers)
  }

  async function regenerateDescription(bookMd5: string) {
    markDescriptionLoading(bookMd5, true)
    try {
      const generated = await SearchService.regenerateBookDescription(bookMd5)
      applyDescription(bookMd5, generated.description, generated.source)
      markDescriptionMissing(bookMd5, false)
    } catch {
      // The stored description is left as it was; say nothing louder than that.
    } finally {
      markDescriptionLoading(bookMd5, false)
    }
  }

  async function fetchLibrary(page: number) {
    loading.value = true
    error.value = null

    try {
      const response = await LibraryService.getUserLibrary(page, pagination.value.pageSize)
      books.value = response.items
      canRegenerate.value = response.canRegenerate
      pagination.value = {
        page: response.page,
        pageSize: response.pageSize,
        totalPages: response.totalPages,
        totalItems: response.totalItems,
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load library'
      books.value = []
    } finally {
      loading.value = false
    }
  }

  function updateDownloadStatus(bookMd5: string, status: DownloadStatusResponse) {
    const updated = new Map(activeDownloads.value)
    updated.set(bookMd5, status)
    activeDownloads.value = updated
  }

  function pollDownload(bookMd5: string, jobId: number) {
    if (downloadIntervals.has(bookMd5)) return

    const intervalId = setInterval(async () => {
      try {
        const status = await DownloadService.getDownloadStatus(jobId)
        updateDownloadStatus(bookMd5, status)

        if (TERMINAL_DOWNLOAD_STATUSES.includes(status.status)) {
          clearInterval(intervalId)
          downloadIntervals.delete(bookMd5)

          if (status.status === 'completed') {
            await fetchLibrary(pagination.value.page)
          }
        }
      } catch {
        const current = activeDownloads.value.get(bookMd5)
        if (current) {
          updateDownloadStatus(bookMd5, {
            ...current,
            error: 'Status updates are temporarily unavailable. Retrying…',
          })
        }
      }
    }, POLL_INTERVAL_MS)

    downloadIntervals.set(bookMd5, intervalId)
  }

  async function fetchDownloadStatuses() {
    try {
      const response = await DownloadService.getDownloadJobs(undefined, 1, 100)
      const latestByBook = new Map<string, DownloadStatusResponse>()

      for (const job of response.items) {
        if (latestByBook.has(job.bookMd5)) continue
        latestByBook.set(job.bookMd5, {
          jobId: job.jobId,
          status: job.status,
          progress: job.progress,
          filePath: job.filePath,
          error: job.error,
        })
      }

      activeDownloads.value = latestByBook
      for (const [bookMd5, status] of latestByBook) {
        if (!TERMINAL_DOWNLOAD_STATUSES.includes(status.status)) {
          pollDownload(bookMd5, status.jobId)
        }
      }
    } catch {
      // The library remains usable even when historical job state is unavailable.
    }
  }

  async function fetchDeviceSettings() {
    try {
      const settings = await SettingsService.getAllSettings()
      deviceSettings.value = {
        kindle: !!settings['kindle'],
        pocketbook: !!settings['pocketbook'],
      }
    } catch {
      deviceSettings.value = { kindle: false, pocketbook: false }
    }
  }

  async function fetchDeliveries() {
    try {
      const records = await DeliverService.getUserDeliveries()
      const grouped = new Map<string, DeliveryRecord[]>()
      for (const record of records) {
        const existing = grouped.get(record.bookMd5) ?? []
        existing.push(record)
        grouped.set(record.bookMd5, existing)
      }
      deliveries.value = grouped
    } catch {
      deliveries.value = new Map()
    }
  }

  async function startDownloadPolling(bookMd5: string) {
    try {
      const started = await DownloadService.startDownload(bookMd5)
      const initialStatus: DownloadStatusResponse = {
        jobId: started.jobId,
        status: started.status,
        progress: 0,
      }

      updateDownloadStatus(bookMd5, initialStatus)
      pollDownload(bookMd5, started.jobId)
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to start download'
    }
  }

  async function startConversionPolling(libraryId: number, targetFormat: 'epub' | 'mobi' | 'pdf') {
    try {
      const started = await ConvertService.startConversion(libraryId, targetFormat)
      const initialStatus: ConversionStatusResponse = {
        jobId: started.jobId,
        status: started.status as ConversionStatusResponse.status,
        sourceFormat: '',
        targetFormat,
      }

      const next = new Map(activeConversions.value)
      next.set(libraryId, initialStatus)
      activeConversions.value = next

      const intervalId = setInterval(async () => {
        try {
          const status = await ConvertService.getConversionStatus(started.jobId)
          const updated = new Map(activeConversions.value)
          updated.set(libraryId, status)
          activeConversions.value = updated

          if (TERMINAL_CONVERSION_STATUSES.includes(status.status)) {
            clearInterval(intervalId)
            conversionIntervals.delete(libraryId)

            if (status.status === 'completed') {
              await fetchLibrary(pagination.value.page)
            }
          }
        } catch {
          clearInterval(intervalId)
          conversionIntervals.delete(libraryId)
        }
      }, POLL_INTERVAL_MS)

      conversionIntervals.set(libraryId, intervalId)
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to start conversion'
    }
  }

  async function deliverBook(libraryId: number, device: 'kindle' | 'pocketbook') {
    try {
      await DeliverService.deliverBook(libraryId, device)
      await fetchDeliveries()
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to deliver book'
      throw err
    }
  }

  async function removeBook(libraryId: number) {
    const previousBooks = [...books.value]

    books.value = books.value.filter((book) => book.id !== libraryId)

    try {
      await LibraryService.removeFromLibrary(libraryId)
    } catch (err) {
      books.value = previousBooks
      error.value = err instanceof Error ? err.message : 'Failed to remove book'
      throw err
    }
  }

  async function downloadFile(libraryId: number) {
    const response = await apiClient.get(`/library/${libraryId}/file`, { responseType: 'blob' })
    const blob = response.data as Blob
    const url = URL.createObjectURL(blob)

    const disposition = response.headers['content-disposition'] as string | undefined
    const filenameMatch = disposition?.match(/filename="?([^"]+)"?/)
    const filename = filenameMatch?.[1] ?? `book-${libraryId}`

    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()

    URL.revokeObjectURL(url)
  }

  function cleanup() {
    for (const intervalId of downloadIntervals.values()) {
      clearInterval(intervalId)
    }
    downloadIntervals.clear()

    for (const intervalId of conversionIntervals.values()) {
      clearInterval(intervalId)
    }
    conversionIntervals.clear()
  }

  return {
    books,
    pagination,
    activeDownloads,
    activeConversions,
    deliveries,
    deviceSettings,
    canRegenerate,
    loading,
    error,
    hasBooks,
    isEmpty,
    isDownloading,
    isConverting,
    getDeliveries,
    isDescriptionLoading,
    isDescriptionMissing,
    resolveMissingDescriptions,
    regenerateDescription,
    fetchLibrary,
    fetchDownloadStatuses,
    fetchDeviceSettings,
    fetchDeliveries,
    startDownloadPolling,
    startConversionPolling,
    deliverBook,
    removeBook,
    downloadFile,
    cleanup,
  }
})
