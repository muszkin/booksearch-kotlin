import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import LogsView from '../LogsView.vue'
import { LogsService, CancelablePromise, type ActivityLogListResponse, type RequestLogListResponse } from '@/api/generated'
import { useAuthStore } from '@/stores/auth'

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

const mockActivityLogs = {
  items: [
    {
      id: 1,
      actionType: 'download',
      entityType: 'book',
      entityId: 'abc123',
      details: 'Downloaded a book',
      createdAt: '2026-01-01T00:00:00Z',
    },
  ],
  totalCount: 1,
} as ActivityLogListResponse

const mockRequestLogs = {
  items: [
    {
      id: 1,
      method: 'GET',
      path: '/api/search',
      statusCode: 200,
      durationMs: 42,
      requestId: 'req-1',
      userId: 1,
      createdAt: '2026-01-01T00:00:00Z',
    },
  ],
  totalCount: 1,
} as RequestLogListResponse

describe('LogsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(LogsService.getActivityLogs).mockReturnValue(resolving(mockActivityLogs))
    vi.mocked(LogsService.getRequestLogs).mockReturnValue(resolving(mockRequestLogs))
  })

  it('renders activity logs tab for regular user', async () => {
    const authStore = useAuthStore()
    authStore.user = {
      id: 2,
      email: 'user@example.com',
      displayName: 'User',
      isSuperAdmin: false,
      isActive: true,
      forcePasswordChange: false,
      createdAt: '2026-01-01T00:00:00Z',
    }

    const wrapper = mount(LogsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Activity Logs')
    expect(wrapper.text()).toContain('download')
  })

  it('renders request logs tab only for admin', async () => {
    const authStore = useAuthStore()
    authStore.user = {
      id: 1,
      email: 'admin@example.com',
      displayName: 'Admin',
      isSuperAdmin: true,
      isActive: true,
      forcePasswordChange: false,
      createdAt: '2026-01-01T00:00:00Z',
    }

    const wrapper = mount(LogsView)
    await flushPromises()

    const tabs = wrapper.findAll('button[role="tab"]')
    const tabLabels = tabs.map((t) => t.text())
    expect(tabLabels).toContain('Request Logs')
  })
})
