import { describe, it, expect } from 'vitest'
import apiClient from './client'

const UUID_V4_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

describe('apiClient', () => {
  it('attaches X-Request-Id header via request interceptor', async () => {
    const interceptors = apiClient.interceptors.request as any
    const handlers = interceptors.handlers.filter((h: any) => h !== null)

    expect(handlers.length).toBeGreaterThanOrEqual(1)

    const config = { headers: {} as Record<string, string> }
    const result = await handlers[0].fulfilled(config)

    expect(result.headers['X-Request-Id']).toBeDefined()
  })

  it('generates a valid UUID v4 for X-Request-Id header', async () => {
    const interceptors = apiClient.interceptors.request as any
    const handlers = interceptors.handlers.filter((h: any) => h !== null)

    const config = { headers: {} as Record<string, string> }
    const result = await handlers[0].fulfilled(config)

    expect(result.headers['X-Request-Id']).toMatch(UUID_V4_REGEX)
  })
})
