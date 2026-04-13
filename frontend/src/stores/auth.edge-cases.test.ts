import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from './auth'
import apiClient from '@/api/client'

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

describe('useAuthStore edge cases', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorageMock.clear()
  })

  it('propagates error when login API call fails', async () => {
    ;(apiClient.post as Mock).mockRejectedValueOnce(new Error('Network error'))

    const store = useAuthStore()

    await expect(store.login({ email: 'a@b.com', password: 'pass1234' }))
      .rejects.toThrow('Network error')

    expect(store.accessToken).toBeNull()
    expect(store.user).toBeNull()
  })

  it('clears state even when logout API call fails', async () => {
    const loginResponse = {
      accessToken: 'token-a',
      refreshToken: 'token-r',
      user: {
        id: 1,
        email: 'a@b.com',
        displayName: 'User',
        isSuperAdmin: false,
        isActive: true,
        forcePasswordChange: false,
        createdAt: '2026-01-01T00:00:00Z',
      },
    }

    ;(apiClient.post as Mock).mockResolvedValueOnce({ data: loginResponse })
    const store = useAuthStore()
    await store.login({ email: 'a@b.com', password: 'pass1234' })

    ;(apiClient.post as Mock).mockRejectedValueOnce(new Error('Server down'))
    await store.logout().catch(() => {})

    expect(store.accessToken).toBeNull()
    expect(store.refreshToken).toBeNull()
    expect(store.user).toBeNull()
    expect(localStorage.getItem('accessToken')).toBeNull()
  })
})
