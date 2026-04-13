import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from './auth'

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

describe('useAuthStore restoreSession edge cases', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorageMock.clear()
  })

  it('does not authenticate when only accessToken is in localStorage (missing refreshToken)', () => {
    localStorageMock.setItem('accessToken', 'partial-token')

    const store = useAuthStore()
    store.restoreSession()

    expect(store.isAuthenticated).toBe(false)
    expect(store.accessToken).toBeNull()
  })

  it('does not authenticate when only refreshToken is in localStorage (missing accessToken)', () => {
    localStorageMock.setItem('refreshToken', 'partial-refresh')

    const store = useAuthStore()
    store.restoreSession()

    expect(store.isAuthenticated).toBe(false)
    expect(store.refreshToken).toBeNull()
  })
})
