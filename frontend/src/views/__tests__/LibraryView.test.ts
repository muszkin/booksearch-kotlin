import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import LibraryView from '../LibraryView.vue'
import { useLibraryStore } from '@/stores/library'
import type { LibraryBook } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    LibraryService: {
      getUserLibrary: vi.fn().mockResolvedValue({ items: [], page: 1, pageSize: 20, totalPages: 0, totalItems: 0 }),
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
      getUserDeliveries: vi.fn().mockResolvedValue([]),
    },
    SettingsService: {
      getAllSettings: vi.fn().mockResolvedValue({}),
    },
  }
})

const mockBook: LibraryBook = {
  id: 1,
  bookMd5: 'abc123',
  format: 'epub',
  filePath: '/books/test.epub',
  addedAt: '2026-01-01T00:00:00Z',
  title: 'Test Book',
  author: 'Test Author',
  language: 'pl',
  fileSize: '1.2 MB',
  detailUrl: '/detail/abc123',
  coverUrl: '/covers/abc123.jpg',
  publisher: 'Publisher',
  year: '2025',
  description: 'A test book',
}

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: LibraryView },
      { path: '/search', name: 'search', component: { template: '<div />' } },
    ],
  })
}

async function mountView() {
  const router = createTestRouter()
  await router.push('/')
  await router.isReady()

  const wrapper = mount(LibraryView, {
    global: {
      plugins: [router],
    },
  })
  return wrapper
}

describe('LibraryView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows loading skeletons while fetching', async () => {
    const wrapper = await mountView()
    const store = useLibraryStore()

    store.loading = true
    await flushPromises()

    const skeletons = wrapper.findAll('[data-testid="skeleton-card"]')
    expect(skeletons.length).toBe(6)
    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(true)
  })

  it('shows EmptyState with search link when library has zero books', async () => {
    const wrapper = await mountView()
    const store = useLibraryStore()

    store.loading = false
    store.error = null
    store.books = []
    await flushPromises()

    expect(wrapper.text()).toContain('Your library is empty')
    expect(wrapper.text()).toContain('Books you download will appear here.')

    const link = wrapper.find('a[href="/search"]')
    expect(link.exists()).toBe(true)
    expect(link.text()).toContain('Search for books')
  })

  it('shows AlertMessage with retry button on fetch error', async () => {
    const wrapper = await mountView()
    const store = useLibraryStore()

    store.error = 'Network error'
    store.loading = false
    await flushPromises()

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('Network error')

    const retryButton = wrapper.find('[data-testid="retry-btn"]')
    expect(retryButton.exists()).toBe(true)
  })

  it('renders LibraryBookCard grid when books are loaded', async () => {
    const wrapper = await mountView()
    const store = useLibraryStore()

    store.books = [mockBook, { ...mockBook, id: 2, bookMd5: 'def456', title: 'Second Book' }]
    store.pagination = { page: 1, pageSize: 20, totalPages: 1, totalItems: 2 }
    store.loading = false
    store.error = null
    await flushPromises()

    const cards = wrapper.findAll('[data-testid="library-book-card"]')
    expect(cards).toHaveLength(2)
    expect(wrapper.text()).toContain('Test Book')
    expect(wrapper.text()).toContain('Second Book')
  })

  it('page change calls store fetchLibrary with new page number', async () => {
    const wrapper = await mountView()
    const store = useLibraryStore()

    store.books = [mockBook]
    store.pagination = { page: 1, pageSize: 20, totalPages: 3, totalItems: 50 }
    store.loading = false
    store.error = null
    await flushPromises()

    const fetchSpy = vi.spyOn(store, 'fetchLibrary')

    const nextButton = wrapper.find('button[aria-label="Next page"]')
    expect(nextButton.exists()).toBe(true)
    await nextButton.trigger('click')

    expect(fetchSpy).toHaveBeenCalledWith(2)
  })

  it('download-file event calls store.downloadFile with book id', async () => {
    const wrapper = await mountView()
    const store = useLibraryStore()

    store.books = [mockBook]
    store.pagination = { page: 1, pageSize: 20, totalPages: 1, totalItems: 1 }
    store.loading = false
    store.error = null
    await flushPromises()

    const downloadFileSpy = vi.spyOn(store, 'downloadFile')

    const downloadBtn = wrapper.find('[data-testid="download-file-btn"]')
    expect(downloadBtn.exists()).toBe(true)
    await downloadBtn.trigger('click')

    expect(downloadFileSpy).toHaveBeenCalledWith(1)
  })

  it('calls store.cleanup on unmount', async () => {
    const wrapper = await mountView()
    const store = useLibraryStore()

    const cleanupSpy = vi.spyOn(store, 'cleanup')

    wrapper.unmount()

    expect(cleanupSpy).toHaveBeenCalledOnce()
  })
})
