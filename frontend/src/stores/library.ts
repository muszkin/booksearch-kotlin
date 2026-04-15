import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import apiClient from '@/api/client'
import {
  LibraryService,
  DownloadService,
  ConvertService,
  DeliverService,
  SettingsService,
} from '@/api/generated'
import type {
  LibraryBook,
  DownloadStatusResponse,
  ConversionStatusResponse,
  DeliveryRecord,
} from '@/api/generated'

const POLL_INTERVAL_MS = 5000
const TERMINAL_DOWNLOAD_STATUSES = ['completed', 'failed']
const TERMINAL_CONVERSION_STATUSES = ['completed', 'failed']

export const useLibraryStore = defineStore('library', () => {
  const books = ref<LibraryBook[]>([])
  const pagination = ref({ page: 1, pageSize: 20, totalPages: 0, totalItems: 0 })
  const activeDownloads = ref(new Map<string, DownloadStatusResponse>())
  const activeConversions = ref(new Map<number, ConversionStatusResponse>())
  const deliveries = ref(new Map<string, DeliveryRecord[]>())
  const deviceSettings = ref({ kindle: false, pocketbook: false })
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

  async function fetchLibrary(page: number) {
    loading.value = true
    error.value = null

    try {
      const response = await LibraryService.getUserLibrary(page, pagination.value.pageSize)
      books.value = response.items
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

      const next = new Map(activeDownloads.value)
      next.set(bookMd5, initialStatus)
      activeDownloads.value = next

      const intervalId = setInterval(async () => {
        try {
          const status = await DownloadService.getDownloadStatus(started.jobId)
          const updated = new Map(activeDownloads.value)
          updated.set(bookMd5, status)
          activeDownloads.value = updated

          if (TERMINAL_DOWNLOAD_STATUSES.includes(status.status)) {
            clearInterval(intervalId)
            downloadIntervals.delete(bookMd5)

            if (status.status === 'completed') {
              await fetchLibrary(pagination.value.page)
            }
          }
        } catch {
          clearInterval(intervalId)
          downloadIntervals.delete(bookMd5)
        }
      }, POLL_INTERVAL_MS)

      downloadIntervals.set(bookMd5, intervalId)
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
    loading,
    error,
    hasBooks,
    isEmpty,
    isDownloading,
    isConverting,
    getDeliveries,
    fetchLibrary,
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
