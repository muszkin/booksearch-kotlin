/**
 * @vitest-environment jsdom
 */
import { describe, it, expect } from 'vitest'
import apiClient from './client'

describe('apiClient interceptors', () => {
  it('has request interceptors configured (X-Request-Id + auth)', () => {
    const interceptors = (apiClient.interceptors.request as any).handlers
    const active = interceptors.filter((h: any) => h !== null)
    // X-Request-Id interceptor added at import time; auth interceptor added in main.ts
    expect(active.length).toBeGreaterThanOrEqual(1)
  })

  it('has response interceptor for 401 handling', () => {
    const interceptors = (apiClient.interceptors.response as any).handlers
    const active = interceptors.filter((h: any) => h !== null)
    expect(active.length).toBeGreaterThanOrEqual(1)
  })
})
