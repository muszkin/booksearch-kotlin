import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { LogsService } from '@/api/generated'
import type { ActivityLogItem, RequestLogItem } from '@/api/generated'

const PAGE_SIZE = 20

export interface RequestLogFilters {
  method?: string
  path?: string
  statusCode?: number
  from?: string
  to?: string
}

export const useLogsStore = defineStore('logs', () => {
  const activityLogs = ref<ActivityLogItem[]>([])
  const requestLogs = ref<RequestLogItem[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const currentTab = ref('activity')

  const activityPage = ref(1)
  const totalActivityCount = ref(0)
  const activityTypeFilter = ref<string | undefined>(undefined)

  const requestPage = ref(1)
  const totalRequestCount = ref(0)
  const requestFilters = ref<RequestLogFilters>({})

  const totalActivityPages = computed(() => Math.max(1, Math.ceil(totalActivityCount.value / PAGE_SIZE)))
  const totalRequestPages = computed(() => Math.max(1, Math.ceil(totalRequestCount.value / PAGE_SIZE)))

  async function fetchActivityLogs(page?: number, type?: string) {
    loading.value = true
    error.value = null

    const targetPage = page ?? activityPage.value
    const filterType = type ?? activityTypeFilter.value

    try {
      const response = await LogsService.getActivityLogs(targetPage, PAGE_SIZE, filterType)
      activityLogs.value = response.items
      totalActivityCount.value = response.totalCount
      activityPage.value = targetPage
      activityTypeFilter.value = filterType
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load activity logs'
      activityLogs.value = []
    } finally {
      loading.value = false
    }
  }

  async function fetchRequestLogs(page?: number, filters?: RequestLogFilters) {
    loading.value = true
    error.value = null

    const targetPage = page ?? requestPage.value
    const f = filters ?? requestFilters.value

    try {
      const response = await LogsService.getRequestLogs(
        targetPage,
        PAGE_SIZE,
        f.method,
        f.path,
        f.statusCode,
        f.from,
        f.to,
      )
      requestLogs.value = response.items
      totalRequestCount.value = response.totalCount
      requestPage.value = targetPage
      requestFilters.value = f
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load request logs'
      requestLogs.value = []
    } finally {
      loading.value = false
    }
  }

  function clearFilters() {
    activityTypeFilter.value = undefined
    requestFilters.value = {}
    activityPage.value = 1
    requestPage.value = 1
  }

  return {
    activityLogs,
    requestLogs,
    loading,
    error,
    currentTab,
    activityPage,
    totalActivityCount,
    totalActivityPages,
    activityTypeFilter,
    requestPage,
    totalRequestCount,
    totalRequestPages,
    requestFilters,
    fetchActivityLogs,
    fetchRequestLogs,
    clearFilters,
  }
})
