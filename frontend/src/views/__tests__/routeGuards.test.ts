import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { setupRouteGuards } from '@/router/guards'

const { apiClientGetMock } = vi.hoisted(() => ({ apiClientGetMock: vi.fn() }))

vi.mock('@/api/client', () => {
  const interceptors = {
    request: { use: vi.fn(), eject: vi.fn() },
    response: { use: vi.fn(), eject: vi.fn() },
  }
  return {
    default: {
      post: vi.fn(),
      get: apiClientGetMock,
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
      { path: '/settings', name: 'settings', meta: { requiresAuth: true }, component: { template: '<div>Settings</div>' } },
      { path: '/admin', name: 'admin', meta: { requiresAuth: true, requiresSuperAdmin: true }, component: { template: '<div>Admin</div>' } },
    ],
  })

  setupRouteGuards(router)
  return router
}

describe('Route guards', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    apiClientGetMock.mockReset()
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
    authStore.user = {
      id: 2,
      email: 'user@example.com',
      displayName: 'User',
      isSuperAdmin: false,
      isActive: true,
      forcePasswordChange: false,
      createdAt: '2026-01-01T00:00:00Z',
    }

    await router.push('/login')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('search')
  })

  it('redirects non-super-admin from /admin to /settings', async () => {
    const router = createGuardedRouter()
    const authStore = useAuthStore()
    authStore.restoreSession = vi.fn()
    authStore.accessToken = 'valid-token'
    authStore.user = {
      id: 2,
      email: 'user@example.com',
      displayName: 'User',
      isSuperAdmin: false,
      isActive: true,
      forcePasswordChange: false,
      createdAt: '2026-01-01T00:00:00Z',
    }

    await router.push('/admin')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('settings')
  })

  it('allows super-admin to access /admin', async () => {
    const router = createGuardedRouter()
    const authStore = useAuthStore()
    authStore.restoreSession = vi.fn()
    authStore.accessToken = 'valid-token'
    authStore.user = {
      id: 1,
      email: 'admin@example.com',
      displayName: 'Admin',
      isSuperAdmin: true,
      isActive: true,
      forcePasswordChange: false,
      createdAt: '2026-01-01T00:00:00Z',
    }

    await router.push('/admin')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('admin')
  })

  it('requiresSuperAdmin route with tokens but user=null awaits loadCurrentUser before the admin gate', async () => {
    localStorage.setItem('accessToken', 'valid-token')
    localStorage.setItem('refreshToken', 'valid-refresh')

    const router = createGuardedRouter()
    const authStore = useAuthStore()

    apiClientGetMock.mockResolvedValue({
      data: {
        id: 1,
        email: 'admin@example.com',
        displayName: 'Admin',
        isSuperAdmin: true,
        isActive: true,
        forcePasswordChange: false,
        createdAt: '2026-01-01T00:00:00Z',
      },
    })

    await router.push('/admin')
    await router.isReady()

    expect(apiClientGetMock).toHaveBeenCalledWith('/auth/me')
    expect(router.currentRoute.value.name).toBe('admin')
    expect(authStore.user?.isSuperAdmin).toBe(true)
  })

  it('requiresSuperAdmin route with tokens but user=null and failing /auth/me redirects to /login with returnUrl', async () => {
    localStorage.setItem('accessToken', 'valid-token')
    localStorage.setItem('refreshToken', 'valid-refresh')

    const router = createGuardedRouter()
    const authStore = useAuthStore()

    apiClientGetMock.mockRejectedValue(new Error('401 unauthorized'))

    await router.push('/admin')
    await router.isReady()

    expect(apiClientGetMock).toHaveBeenCalledWith('/auth/me')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.returnUrl).toBe('/admin')
    expect(authStore.user).toBeNull()
  })
})
