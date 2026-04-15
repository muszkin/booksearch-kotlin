import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useLogsStore } from './logs'
import { LogsService, CancelablePromise } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    LogsService: {
      getActivityLogs: vi.fn(),
      getRequestLogs: vi.fn(),
    },
  }
})

function resolving<T>(value: T): CancelablePromise<T> {
  return new CancelablePromise((resolve) => resolve(value))
}

describe('logs store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetches activity logs and updates pagination', async () => {
    vi.mocked(LogsService.getActivityLogs).mockReturnValue(
      resolving({
        items: [
          {
            id: 1,
            actionType: 'download',
            entityType: 'book',
            entityId: 'abc',
            details: 'Test',
            createdAt: '2026-01-01T00:00:00Z',
          },
          {
            id: 2,
            actionType: 'login',
            entityType: 'user',
            entityId: null,
            details: null,
            createdAt: '2026-01-01T00:01:00Z',
          },
        ],
        totalCount: 25,
      }),
    )

    const store = useLogsStore()
    await store.fetchActivityLogs()

    expect(store.activityLogs).toHaveLength(2)
    expect(store.totalActivityCount).toBe(25)
    expect(LogsService.getActivityLogs).toHaveBeenCalledWith(1, 20, undefined)
  })

  it('fetches activity logs with page and type filter', async () => {
    vi.mocked(LogsService.getActivityLogs).mockReturnValue(
      resolving({ items: [], totalCount: 0 }),
    )

    const store = useLogsStore()
    await store.fetchActivityLogs(3, 'download')

    expect(LogsService.getActivityLogs).toHaveBeenCalledWith(3, 20, 'download')
  })
})
