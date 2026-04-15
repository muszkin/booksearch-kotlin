import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import SearchView from '../SearchView.vue'
import LoginView from '../LoginView.vue'
import { useSearchStore } from '@/stores/search'
import { useLibraryStore } from '@/stores/library'
import { useAuthStore } from '@/stores/auth'
import { BookResult, DownloadService, AuthService } from '@/api/generated'
import { setupRouteGuards } from '@/router/guards'

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
    AuthService: {
      getRegistrationStatus: vi.fn(),
    },
  }
})

vi.mock('@/api/client', () => {
  const interceptors = {
    request: { use: vi.fn(), eject: vi.fn() },
    response: { use: vi.fn(), eject: vi.fn() },
  }
  return {
    default: {
      get: vi.fn(),
      post: vi.fn(),
      interceptors,
      defaults: { headers: { common: {} } },
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

describe('Bug Fix: SearchView.handleDownload awaits promise and shows error on failure', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows error message when download fails', async () => {
    ;(DownloadService.startDownload as Mock).mockRejectedValueOnce(new Error('Download failed'))

    const wrapper = mount(SearchView)
    const searchStore = useSearchStore()

    searchStore.query = 'test'
    searchStore.results = [mockBook]
    await flushPromises()

    const downloadBtn = wrapper.find('[data-testid="download-btn"]')
    expect(downloadBtn.exists()).toBe(true)

    await downloadBtn.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Download failed')
  })
})

describe('Bug Fix: Library downloadFile uses axios blob download', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('downloads file via axios blob instead of window.open', async () => {
    const apiClient = (await import('@/api/client')).default
    const mockBlob = new Blob(['file content'], { type: 'application/epub+zip' })
    ;(apiClient.get as Mock).mockResolvedValueOnce({
      data: mockBlob,
      headers: { 'content-disposition': 'attachment; filename="book.epub"' },
    })

    const createObjectURLMock = vi.fn().mockReturnValue('blob:http://localhost/fake-url')
    const revokeObjectURLMock = vi.fn()
    ;(globalThis as any).URL.createObjectURL = createObjectURLMock
    ;(globalThis as any).URL.revokeObjectURL = revokeObjectURLMock

    const clickMock = vi.fn()
    vi.spyOn(document, 'createElement').mockReturnValueOnce({
      href: '',
      download: '',
      click: clickMock,
      style: {},
    } as unknown as HTMLAnchorElement)

    const libraryStore = useLibraryStore()
    await libraryStore.downloadFile(1)

    expect(apiClient.get).toHaveBeenCalledWith('/library/1/file', { responseType: 'blob' })
    expect(createObjectURLMock).toHaveBeenCalled()
    expect(clickMock).toHaveBeenCalled()
    expect(revokeObjectURLMock).toHaveBeenCalled()
  })
})

describe('Bug Fix: LoginView hides register link when registration is disabled', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  function createTestRouter() {
    return createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/login', name: 'login', component: LoginView },
        { path: '/register', name: 'register', component: { template: '<div>Register</div>' } },
        { path: '/search', name: 'search', component: { template: '<div>Search</div>' } },
      ],
    })
  }

  it('hides register link when registration is disabled', async () => {
    ;(AuthService.getRegistrationStatus as Mock).mockResolvedValueOnce({ enabled: false })

    const router = createTestRouter()
    const pinia = createPinia()
    setActivePinia(pinia)

    await router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, {
      global: { plugins: [pinia, router] },
    })

    await flushPromises()

    const links = wrapper.findAll('a')
    const registerLink = links.find((link) => link.attributes('href') === '/register')
    expect(registerLink).toBeUndefined()
  })
})

describe('Bug Fix: /register route redirects to /login when registration is disabled', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('redirects /register to /login when registration is disabled', async () => {
    ;(AuthService.getRegistrationStatus as Mock).mockResolvedValueOnce({ enabled: false })

    const pinia = createPinia()
    setActivePinia(pinia)

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/login', name: 'login', meta: { guest: true }, component: { template: '<div>Login</div>' } },
        { path: '/register', name: 'register', meta: { guest: true, checkRegistration: true }, component: { template: '<div>Register</div>' } },
        { path: '/search', name: 'search', meta: { requiresAuth: true }, component: { template: '<div>Search</div>' } },
      ],
    })

    const authStore = useAuthStore()
    authStore.restoreSession = vi.fn()

    setupRouteGuards(router)

    await router.push('/register')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
  })
})
