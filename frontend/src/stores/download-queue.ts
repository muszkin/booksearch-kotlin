import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { DownloadService } from '@/api/generated'
import type { DownloadJobItem } from '@/api/generated'

const POLL_INTERVAL_MS = 5000
const PAGE_SIZE = 20

export const useDownloadQueueStore = defineStore('download-queue', () => {
  const jobs = ref<DownloadJobItem[]>([])
  const loading = ref(false)
  const currentTab = ref('active')
  const page = ref(1)
  const totalCount = ref(0)
  const error = ref<string | null>(null)

  let pollIntervalId: ReturnType<typeof setInterval> | null = null

  const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / PAGE_SIZE)))
  const isEmpty = computed(() => jobs.value.length === 0 && !loading.value && !error.value)

  async function fetchJobs(status?: string, requestedPage?: number) {
    loading.value = true
    error.value = null

    const filterStatus = status ?? currentTab.value
    const targetPage = requestedPage ?? page.value

    try {
      const response = await DownloadService.getDownloadJobs(filterStatus, targetPage, PAGE_SIZE)
      jobs.value = response.items
      totalCount.value = response.totalCount
      page.value = targetPage
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load download jobs'
      jobs.value = []
    } finally {
      loading.value = false
    }
  }

  function addOptimisticJob(jobId: number, bookMd5?: string, extras: Partial<DownloadJobItem> = {}) {
    if (jobs.value.some((j) => j.jobId === jobId)) return
    const now = new Date().toISOString()
    const optimistic: DownloadJobItem = {
      jobId,
      bookMd5: bookMd5 ?? extras.bookMd5 ?? '',
      format: extras.format ?? '',
      status: extras.status ?? 'queued',
      progress: extras.progress ?? 0,
      createdAt: extras.createdAt ?? now,
      updatedAt: extras.updatedAt ?? now,
      ...extras,
    }
    jobs.value = [optimistic, ...jobs.value]
    totalCount.value = totalCount.value + 1
    fetchJobs()
  }

  async function cancelJob(jobId: number) {
    try {
      await DownloadService.cancelDownloadJob(jobId)
      await fetchJobs()
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to cancel job'
    }
  }

  function startPolling() {
    stopPolling()
    pollIntervalId = setInterval(() => {
      fetchJobs()
    }, POLL_INTERVAL_MS)
  }

  function stopPolling() {
    if (pollIntervalId !== null) {
      clearInterval(pollIntervalId)
      pollIntervalId = null
    }
  }

  function setTab(tab: string) {
    currentTab.value = tab
    page.value = 1
    fetchJobs(tab, 1)
  }

  return {
    jobs,
    loading,
    currentTab,
    page,
    totalCount,
    totalPages,
    error,
    isEmpty,
    fetchJobs,
    addOptimisticJob,
    cancelJob,
    startPolling,
    stopPolling,
    setTab,
  }
})
