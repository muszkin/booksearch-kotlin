import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import SearchView from '../SearchView.vue'
import { useSearchStore } from '@/stores/search'
import { BookResult, SearchService } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    SearchService: {
      submitSearch: vi.fn(),
      getSearchStatus: vi.fn(),
      getBookDescription: vi.fn(),
    },
    DownloadService: { startDownload: vi.fn() },
    LibraryService: { addToLibrary: vi.fn() },
  }
})

const BLURB = 'Stacja badawcza na orbicie myślącego oceanu, który odpowiada uczonym wspomnieniami.'

const mockBook: BookResult = {
  md5: 'abc123',
  title: 'Solaris',
  author: 'Stanisław Lem',
  language: 'Polish [pl]',
  format: 'epub',
  fileSize: '1.2 MB',
  detailUrl: '/detail/abc123',
  coverUrl: '/covers/abc123.jpg',
  publisher: 'WL',
  year: '1961',
  description: '',
  matchType: BookResult.matchType.NONE,
  ownedFormats: [],
}

async function mountWithResults() {
  const wrapper = mount(SearchView, { global: { stubs: { Teleport: true } } })
  const searchStore = useSearchStore()
  searchStore.query = 'lem'
  searchStore.results = [mockBook]
  await flushPromises()
  return wrapper
}

describe('SearchView description', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders the description as soon as it arrives, without a second toggle', async () => {
    vi.mocked(SearchService.getBookDescription).mockResolvedValue({
      description: BLURB,
      source: 'annas-archive',
      isbn: '9788308068069',
    })
    const wrapper = await mountWithResults()

    await wrapper.get('[data-testid="description-toggle"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="description-body"]').text()).toContain('Stacja badawcza')
  })

  it('stops showing progress once the description has loaded', async () => {
    vi.mocked(SearchService.getBookDescription).mockResolvedValue({
      description: BLURB,
      source: 'annas-archive',
    })
    const wrapper = await mountWithResults()

    await wrapper.get('[data-testid="description-toggle"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="description-body"]').attributes('aria-busy')).toBe('false')
  })

  it('labels a generated description as soon as it arrives', async () => {
    vi.mocked(SearchService.getBookDescription).mockResolvedValue({
      description: BLURB,
      source: 'openrouter',
    })
    const wrapper = await mountWithResults()

    await wrapper.get('[data-testid="description-toggle"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="description-generated-label"]').text()).toMatch(/AI/i)
  })

  it('reports a missing description without a second toggle', async () => {
    vi.mocked(SearchService.getBookDescription).mockRejectedValue(new Error('404'))
    const wrapper = await mountWithResults()

    await wrapper.get('[data-testid="description-toggle"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="description-body"]').text()).toContain('No description')
  })
})
