import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from './auth'
import apiClient from '@/api/client'
import type { LoginResponse } from '@/api/generated'
import type { UserResponse } from '@/api/generated'

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

const mockUser: UserResponse = {
  id: 1,
  email: 'test@example.com',
  displayName: 'Test User',
  isSuperAdmin: false,
  isActive: true,
  forcePasswordChange: false,
  createdAt: '2026-01-01T00:00:00Z',
}

const mockLoginResponse: LoginResponse = {
  accessToken: 'access-token-123',
  refreshToken: 'refresh-token-456',
  user: mockUser,
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

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorageMock.clear()
  })

  it('stores tokens and user after successful login', async () => {
    ;(apiClient.post as Mock).mockResolvedValueOnce({ data: mockLoginResponse })

    const store = useAuthStore()
    await store.login({ email: 'test@example.com', password: 'password123' })

    expect(store.accessToken).toBe('access-token-123')
    expect(store.refreshToken).toBe('refresh-token-456')
    expect(store.user).toEqual(mockUser)
    expect(localStorage.getItem('accessToken')).toBe('access-token-123')
    expect(localStorage.getItem('refreshToken')).toBe('refresh-token-456')
  })

  it('stores tokens and user after successful registration', async () => {
    ;(apiClient.post as Mock).mockResolvedValueOnce({ data: mockLoginResponse })

    const store = useAuthStore()
    await store.register({
      email: 'test@example.com',
      password: 'password123',
      displayName: 'Test User',
    })

    expect(store.accessToken).toBe('access-token-123')
    expect(store.refreshToken).toBe('refresh-token-456')
    expect(store.user).toEqual(mockUser)
    expect(localStorage.getItem('accessToken')).toBe('access-token-123')
    expect(localStorage.getItem('refreshToken')).toBe('refresh-token-456')
  })

  it('clears state and localStorage on logout', async () => {
    ;(apiClient.post as Mock).mockResolvedValueOnce({ data: mockLoginResponse })

    const store = useAuthStore()
    await store.login({ email: 'test@example.com', password: 'password123' })

    ;(apiClient.post as Mock).mockResolvedValueOnce({})
    await store.logout()

    expect(store.accessToken).toBeNull()
    expect(store.refreshToken).toBeNull()
    expect(store.user).toBeNull()
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
  })

  it('returns correct isAuthenticated getter value', async () => {
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)

    ;(apiClient.post as Mock).mockResolvedValueOnce({ data: mockLoginResponse })
    await store.login({ email: 'test@example.com', password: 'password123' })

    expect(store.isAuthenticated).toBe(true)
  })

  it('restores session from localStorage', () => {
    localStorage.setItem('accessToken', 'stored-access')
    localStorage.setItem('refreshToken', 'stored-refresh')

    const store = useAuthStore()
    store.restoreSession()

    expect(store.accessToken).toBe('stored-access')
    expect(store.refreshToken).toBe('stored-refresh')
    expect(store.isAuthenticated).toBe(true)
  })

  it('refreshes access token using refresh token', async () => {
    ;(apiClient.post as Mock).mockResolvedValueOnce({ data: mockLoginResponse })

    const store = useAuthStore()
    await store.login({ email: 'test@example.com', password: 'password123' })

    ;(apiClient.post as Mock).mockResolvedValueOnce({
      data: {
        accessToken: 'new-access',
        refreshToken: 'rotated-refresh',
        user: mockUser,
      },
    })

    await store.refreshAccessToken()

    expect(store.accessToken).toBe('new-access')
    expect(store.refreshToken).toBe('rotated-refresh')
    expect(store.user).toEqual(mockUser)
    expect(localStorage.getItem('accessToken')).toBe('new-access')
    expect(localStorage.getItem('refreshToken')).toBe('rotated-refresh')
  })
})
