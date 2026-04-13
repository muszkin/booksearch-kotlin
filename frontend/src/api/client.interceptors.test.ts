import { describe, it, expect, beforeEach } from 'vitest'
import apiClient from './client'

const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value },
    removeItem: (key: string) => { delete store[key] },
    clear: () => { Object.keys(store).forEach((k) => delete store[k]) },
  }
})()

if (typeof globalThis.localStorage === 'undefined' || typeof globalThis.localStorage.getItem !== 'function') {
  Object.defineProperty(globalThis, 'localStorage', { value: localStorageMock, writable: true })
}

describe('apiClient request interceptor - Authorization header', () => {
  beforeEach(() => {
    localStorageMock.clear()
  })

  it('attaches Authorization Bearer header when accessToken exists in localStorage', async () => {
    localStorageMock.setItem('accessToken', 'my-jwt-token')

    const interceptors = apiClient.interceptors.request as any
    const handlers = interceptors.handlers.filter((h: any) => h !== null)
    const config = { headers: {} as Record<string, string> }
    const result = await handlers[0].fulfilled(config)

    expect(result.headers['Authorization']).toBe('Bearer my-jwt-token')
  })

  it('does not attach Authorization header when no accessToken in localStorage', async () => {
    const interceptors = apiClient.interceptors.request as any
    const handlers = interceptors.handlers.filter((h: any) => h !== null)
    const config = { headers: {} as Record<string, string> }
    const result = await handlers[0].fulfilled(config)

    expect(result.headers['Authorization']).toBeUndefined()
  })
})
