import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from './auth'
import apiClient from '@/api/client'
import type { LoginResponse, UserResponse } from '@/api/generated'

vi.mock('@/api/client', () => {
  const interceptors = {
    request: { use: vi.fn(), eject: vi.fn() },
    response: { use: vi.fn(), eject: vi.fn() },
  }
  return {
    default: {
      post: vi.fn(),
      get: vi.fn(),
      interceptors,
      defaults: { headers: { common: {} } },
    },
  }
})

const adminUser: UserResponse = {
  id: 1,
  email: 'admin@example.com',
  displayName: 'Super Admin',
  isSuperAdmin: true,
  isActive: true,
  forcePasswordChange: false,
  createdAt: '2026-01-01T00:00:00Z',
}

const impersonatedUser: UserResponse = {
  id: 42,
  email: 'jan@example.com',
  displayName: 'Jan Kowalski',
  isSuperAdmin: false,
  isActive: true,
  forcePasswordChange: false,
  createdAt: '2026-02-01T00:00:00Z',
  actAsUserId: 1,
  actAsEmail: 'admin@example.com',
}

const adminLoginResponse: LoginResponse = {
  accessToken: 'admin-access-token',
  refreshToken: 'admin-refresh-token',
  user: adminUser,
}

const impersonationLoginResponse: LoginResponse = {
  accessToken: 'impersonation-access-token',
  refreshToken: 'impersonation-refresh-token',
  user: impersonatedUser,
}

const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value },
    removeItem: (key: string) => { delete store[key] },
    clear: () => { store = {} },
  }
})()

Object.defineProperty(globalThis, 'localStorage', { value: localStorageMock, writable: true })

describe('useAuthStore — impersonation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorageMock.clear()
  })

  it('startImpersonation posts to admin endpoint and swaps tokens + user', async () => {
    // Seed: admin is logged in.
    ;(apiClient.post as Mock).mockResolvedValueOnce({ data: adminLoginResponse })
    const store = useAuthStore()
    await store.login({ email: 'admin@example.com', password: 'adminpass' })

    expect(store.accessToken).toBe('admin-access-token')
    expect(store.isImpersonating).toBe(false)

    // Act: start impersonation of user 42.
    ;(apiClient.post as Mock).mockResolvedValueOnce({ data: impersonationLoginResponse })
    await store.startImpersonation(42)

    // Assert.
    expect(apiClient.post).toHaveBeenLastCalledWith('/admin/users/42/impersonate', {})
    expect(store.accessToken).toBe('impersonation-access-token')
    expect(store.refreshToken).toBe('impersonation-refresh-token')
    expect(store.user?.id).toBe(42)
    expect(store.user?.actAsUserId).toBe(1)
    expect(store.user?.actAsEmail).toBe('admin@example.com')
    expect(store.isImpersonating).toBe(true)
    expect(store.realAdminEmail).toBe('admin@example.com')
    expect(localStorage.getItem('accessToken')).toBe('impersonation-access-token')
    expect(localStorage.getItem('refreshToken')).toBe('impersonation-refresh-token')
  })

  it('stopImpersonation posts with refresh token and restores admin', async () => {
    // Seed: store is already in an impersonation session.
    ;(apiClient.post as Mock).mockResolvedValueOnce({ data: impersonationLoginResponse })
    const store = useAuthStore()
    await store.login({ email: 'admin@example.com', password: 'adminpass' })
    // Flip the cached login result into an "impersonation" state manually to mimic having started it.
    // (login-mocked with impersonationLoginResponse already leaves us in that state — verify.)
    expect(store.isImpersonating).toBe(true)
    expect(store.refreshToken).toBe('impersonation-refresh-token')

    // Act: stop impersonation.
    ;(apiClient.post as Mock).mockResolvedValueOnce({ data: adminLoginResponse })
    await store.stopImpersonation()

    // Assert: body contained the current (impersonation) refresh token before swap.
    expect(apiClient.post).toHaveBeenLastCalledWith('/admin/impersonate/stop', {
      refreshToken: 'impersonation-refresh-token',
    })
    expect(store.user?.id).toBe(1)
    expect(store.user?.actAsUserId).toBeUndefined()
    expect(store.isImpersonating).toBe(false)
    expect(store.realAdminEmail).toBeNull()
    expect(store.accessToken).toBe('admin-access-token')
    expect(store.refreshToken).toBe('admin-refresh-token')
    expect(localStorage.getItem('accessToken')).toBe('admin-access-token')
    expect(localStorage.getItem('refreshToken')).toBe('admin-refresh-token')
  })

  it('isImpersonating computed toggles with user.actAsUserId', () => {
    const store = useAuthStore()

    // No user yet → not impersonating.
    expect(store.isImpersonating).toBe(false)
    expect(store.realAdminEmail).toBeNull()

    // Non-impersonating user → not impersonating.
    store.user = { ...adminUser }
    expect(store.isImpersonating).toBe(false)
    expect(store.realAdminEmail).toBeNull()

    // Impersonating user → isImpersonating + realAdminEmail populated.
    store.user = {
      ...impersonatedUser,
      actAsUserId: 5,
      actAsEmail: 'other-admin@example.com',
    }
    expect(store.isImpersonating).toBe(true)
    expect(store.realAdminEmail).toBe('other-admin@example.com')
  })
})
