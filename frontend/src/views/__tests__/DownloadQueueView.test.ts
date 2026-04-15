import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import DownloadQueueView from '../DownloadQueueView.vue'
import { DownloadService, CancelablePromise } from '@/api/generated'
import type { DownloadJobListResponse } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    DownloadService: {
      getDownloadJobs: vi.fn(),
      cancelDownloadJob: vi.fn(),
    },
  }
})

function resolving<T>(value: T): CancelablePromise<T> {
  return new CancelablePromise((resolve) => resolve(value))
}

const mockJobList: DownloadJobListResponse = {
  items: [
    {
      jobId: 1,
      bookMd5: 'abc123',
      format: 'epub',
      status: 'active',
      progress: 50,
      filePath: null,
      error: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:01:00Z',
    },
    {
      jobId: 2,
      bookMd5: 'def456',
      format: 'pdf',
      status: 'queued',
      progress: 0,
      filePath: null,
      error: null,
      createdAt: '2026-01-01T00:02:00Z',
      updatedAt: '2026-01-01T00:02:00Z',
    },
  ],
  totalCount: 2,
}

describe('DownloadQueueView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(DownloadService.getDownloadJobs).mockReturnValue(resolving(mockJobList))
    vi.mocked(DownloadService.cancelDownloadJob).mockReturnValue(resolving({ message: 'cancelled' }))
  })

  it('renders job list with status tabs', async () => {
    const wrapper = mount(DownloadQueueView)
    await flushPromises()

    const tabs = wrapper.findAll('button[role="tab"]')
    expect(tabs.length).toBeGreaterThanOrEqual(4)

    expect(wrapper.text()).toContain('epub')
    expect(wrapper.text()).toContain('pdf')
  })

  it('calls cancel endpoint when cancel button is clicked', async () => {
    const wrapper = mount(DownloadQueueView)
    await flushPromises()

    const cancelButton = wrapper.find('[data-testid="cancel-job-1"]')
    expect(cancelButton.exists()).toBe(true)

    await cancelButton.trigger('click')
    await flushPromises()

    expect(DownloadService.cancelDownloadJob).toHaveBeenCalledWith(1)
  })
})
