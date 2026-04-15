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

describe('LibraryView UX refactor', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders books in single-column list layout (flex-col) not grid', async () => {
    const wrapper = await mountView()
    const store = useLibraryStore()

    store.books = [mockBook, { ...mockBook, id: 2, bookMd5: 'def456', title: 'Second Book' }]
    store.pagination = { page: 1, pageSize: 20, totalPages: 1, totalItems: 2 }
    store.loading = false
    store.error = null
    await flushPromises()

    const container = wrapper.find('[data-testid="library-book-list"]')
    expect(container.exists()).toBe(true)
    expect(container.classes()).toContain('flex')
    expect(container.classes()).toContain('flex-col')
    expect(container.classes()).not.toContain('grid')
  })

  it('checkbox selection toggles and tracks selectedIds', async () => {
    const wrapper = await mountView()
    const store = useLibraryStore()

    store.books = [mockBook, { ...mockBook, id: 2, bookMd5: 'def456', title: 'Second Book' }]
    store.pagination = { page: 1, pageSize: 20, totalPages: 1, totalItems: 2 }
    store.loading = false
    store.error = null
    await flushPromises()

    const checkboxes = wrapper.findAll('[data-testid="library-select-checkbox"]')
    expect(checkboxes).toHaveLength(2)

    await checkboxes[0].setValue(true)
    await flushPromises()

    const toolbar = wrapper.find('[data-testid="selection-toolbar"]')
    expect(toolbar.exists()).toBe(true)
    expect(toolbar.text()).toContain('1 selected')
  })
})
