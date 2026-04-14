import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import SearchView from '../SearchView.vue'
import { useSearchStore } from '@/stores/search'
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

describe('SearchView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders SearchToolbar and EmptyState on initial load', () => {
    const wrapper = mount(SearchView)

    expect(wrapper.find('form[role="search"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Search for books')
    expect(wrapper.text()).toContain('Use the search bar to find your next great read.')
  })

  it('shows BookCardSkeleton components when loading is true', async () => {
    const wrapper = mount(SearchView)
    const searchStore = useSearchStore()

    searchStore.loading = true
    await flushPromises()

    const skeletons = wrapper.findAll('[data-testid="skeleton-card"]')
    expect(skeletons.length).toBeGreaterThanOrEqual(3)

    const busyContainer = wrapper.find('[aria-busy="true"]')
    expect(busyContainer.exists()).toBe(true)
  })

  it('renders BookCard list when results has items', async () => {
    const wrapper = mount(SearchView)
    const searchStore = useSearchStore()

    searchStore.query = 'Przestrzen'
    searchStore.results = [mockBook]
    await flushPromises()

    const cards = wrapper.findAll('[data-testid="book-card"]')
    expect(cards).toHaveLength(1)
    expect(wrapper.text()).toContain('Przestrzen objawienia')
  })

  it('shows EmptyState with no books found when search returns empty', async () => {
    const wrapper = mount(SearchView)
    const searchStore = useSearchStore()

    searchStore.query = 'nonexistent'
    searchStore.results = []
    searchStore.loading = false
    await flushPromises()

    expect(wrapper.text()).toContain('No books found')
  })

  it('shows AlertMessage with retry button when error is set', async () => {
    const wrapper = mount(SearchView)
    const searchStore = useSearchStore()

    searchStore.error = 'Network error'
    await flushPromises()

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('Network error')

    const retryButton = wrapper.find('[data-testid="retry-btn"]')
    expect(retryButton.exists()).toBe(true)
  })
})
