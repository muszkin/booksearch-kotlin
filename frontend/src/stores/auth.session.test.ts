import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from './auth'
import apiClient from '@/api/client'
import type { UserResponse } from '@/api/generated'

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

const mockUser: UserResponse = {
  id: 42,
  email: 'alice@example.com',
  displayName: 'Alice',
  isSuperAdmin: false,
  isActive: true,
  forcePasswordChange: false,
  createdAt: '2026-01-01T00:00:00Z',
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

describe('useAuthStore — silent session logout regression', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorageMock.clear()
  })

  it('loadCurrentUser() fetches /me and populates user', async () => {
    ;(apiClient.get as Mock).mockResolvedValueOnce({ data: mockUser })

    const store = useAuthStore()
    localStorage.setItem('accessToken', 'valid-access')
    localStorage.setItem('refreshToken', 'valid-refresh')
    store.restoreSession()

    await store.loadCurrentUser()

    expect(apiClient.get).toHaveBeenCalledWith('/auth/me')
    expect(store.user).toEqual(mockUser)
    expect(store.user?.displayName).toBe('Alice')
  })

  it('refreshAccessToken() updates access, refresh AND user from response', async () => {
    ;(apiClient.post as Mock).mockResolvedValueOnce({
      data: {
        accessToken: 'rotated-access',
        refreshToken: 'rotated-refresh',
        user: mockUser,
      },
    })

    const store = useAuthStore()
    localStorage.setItem('accessToken', 'old-access')
    localStorage.setItem('refreshToken', 'old-refresh')
    store.restoreSession()

    await store.refreshAccessToken()

    expect(store.accessToken).toBe('rotated-access')
    expect(store.refreshToken).toBe('rotated-refresh')
    expect(store.user).toEqual(mockUser)
    expect(localStorage.getItem('accessToken')).toBe('rotated-access')
    expect(localStorage.getItem('refreshToken')).toBe('rotated-refresh')
  })

  it('restoreSession() with tokens present does not leave user null after bootstrap', async () => {
    ;(apiClient.get as Mock).mockResolvedValueOnce({ data: mockUser })

    const store = useAuthStore()
    localStorage.setItem('accessToken', 'stored-access')
    localStorage.setItem('refreshToken', 'stored-refresh')
    store.restoreSession()

    expect(store.isAuthenticated).toBe(true)
    expect(store.user).toBeNull()

    await store.loadCurrentUser()

    expect(apiClient.get).toHaveBeenCalledWith('/auth/me')
    expect(store.user).not.toBeNull()
    expect(store.user?.email).toBe('alice@example.com')
  })
})
