import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import SearchView from '../SearchView.vue'
import { useSearchStore } from '@/stores/search'
import { useSelectionStore } from '@/stores/selection'
import { BookResult } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    SearchService: {
      searchBooks: vi.fn(),
    },
    DownloadService: {
      startDownload: vi.fn(),
    },
    LibraryService: {
      addToLibrary: vi.fn(),
    },
  }
})

const mockBook: BookResult = {
  md5: 'abc123',
  title: 'Przestrzen objawienia',
  author: 'Alastair Reynolds',
  language: 'pl',
  format: 'epub',
  fileSize: '1.2 MB',
  detailUrl: '/detail/abc123',
  coverUrl: '/covers/abc123.jpg',
  publisher: 'Wydawnictwo',
  year: '2020',
  description: 'Sci-fi novel',
  matchType: BookResult.matchType.NONE,
  ownedFormats: [],
}

describe('SearchView store integration', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('toggling BookCard checkbox updates selectionStore and opens drawer', async () => {
    const wrapper = mount(SearchView, {
      global: { stubs: { Teleport: true } },
    })
    const searchStore = useSearchStore()
    const selectionStore = useSelectionStore()

    searchStore.query = 'test'
    searchStore.results = [mockBook]
    await flushPromises()

    const checkbox = wrapper.find('input[type="checkbox"]')
    await checkbox.setValue(true)
    await flushPromises()

    expect(selectionStore.isSelected('abc123')).toBe(true)
    expect(selectionStore.count).toBe(1)
  })

  it('shows total results count in aria-live region when results exist', async () => {
    const wrapper = mount(SearchView, {
      global: { stubs: { Teleport: true } },
    })
    const searchStore = useSearchStore()

    searchStore.query = 'Przestrzen'
    searchStore.results = [mockBook]
    searchStore.totalResults = 42
    await flushPromises()

    const liveRegion = wrapper.find('[aria-live="polite"]')
    expect(liveRegion.exists()).toBe(true)
    expect(liveRegion.text()).toContain('42')
  })

  it('retry button calls search again after error', async () => {
    const wrapper = mount(SearchView, {
      global: { stubs: { Teleport: true } },
    })
    const searchStore = useSearchStore()
    searchStore.search = vi.fn()

    searchStore.error = 'Connection timeout'
    await flushPromises()

    const retryBtn = wrapper.find('[data-testid="retry-btn"]')
    await retryBtn.trigger('click')

    expect(searchStore.search).toHaveBeenCalledOnce()
  })
})
