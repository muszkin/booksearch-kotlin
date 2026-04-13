import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { setupRouteGuards } from '@/router/guards'
import MainLayout from '@/components/layout/MainLayout.vue'
import SearchView from '@/views/SearchView.vue'
import LibraryView from '@/views/LibraryView.vue'
import SettingsView from '@/views/SettingsView.vue'

vi.mock('@/api/client', () => {
  const interceptors = {
    request: { use: vi.fn(), eject: vi.fn() },
    response: { use: vi.fn(), eject: vi.fn() },
  }
  return {
    default: {
      post: vi.fn(),
      interceptors,
      defaults: { headers: { common: {} } },
    },
  }
})

function createAppRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', redirect: '/search' },
      { path: '/login', name: 'login', component: { template: '<div>Login</div>' }, meta: { guest: true } },
      { path: '/register', name: 'register', component: { template: '<div>Register</div>' }, meta: { guest: true } },
      {
        path: '/',
        component: MainLayout,
        meta: { requiresAuth: true },
        children: [
          { path: 'search', name: 'search', component: SearchView },
          { path: 'library', name: 'library', component: LibraryView },
          { path: 'settings', name: 'settings', component: SettingsView },
        ],
      },
    ],
  })
  setupRouteGuards(router)
  return router
}

describe('Application Router', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('redirects root path to /search for authenticated user', async () => {
    const router = createAppRouter()
    const authStore = useAuthStore()
    authStore.restoreSession = vi.fn()
    authStore.accessToken = 'test-token'

    await router.push('/')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('search')
    expect(router.currentRoute.value.path).toBe('/search')
  })
})
