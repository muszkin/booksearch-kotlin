import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { setupRouteGuards } from '@/router/guards'

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

function createGuardedRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div>Home</div>' } },
      { path: '/login', name: 'login', meta: { guest: true }, component: { template: '<div>Login</div>' } },
      { path: '/register', name: 'register', meta: { guest: true }, component: { template: '<div>Register</div>' } },
      { path: '/search', name: 'search', meta: { requiresAuth: true }, component: { template: '<div>Search</div>' } },
    ],
  })

  setupRouteGuards(router)
  return router
}

describe('Route guards', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('redirects unauthenticated user from /search to /login', async () => {
    const router = createGuardedRouter()
    const authStore = useAuthStore()
    authStore.restoreSession = vi.fn()

    await router.push('/search')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('redirects authenticated user from /login to /search', async () => {
    const router = createGuardedRouter()
    const authStore = useAuthStore()
    authStore.restoreSession = vi.fn()
    authStore.accessToken = 'valid-token'

    await router.push('/login')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('search')
  })
})
