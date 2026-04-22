/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { AxiosRequestConfig } from 'axios'

const refreshAccessTokenMock = vi.fn()
const clearStateMock = vi.fn()
const authStoreState: {
  accessToken: string | null
  refreshAccessToken: ReturnType<typeof vi.fn>
  clearState: ReturnType<typeof vi.fn>
} = {
  accessToken: 'old-access',
  refreshAccessToken: refreshAccessTokenMock,
  clearState: clearStateMock,
}

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authStoreState,
}))

const routerPushMock = vi.fn()
const routerCurrentRoute = { value: { fullPath: '/library' } }

vi.mock('@/router', () => ({
  default: {
    push: routerPushMock,
    currentRoute: routerCurrentRoute,
  },
}))

interface AdapterResponse {
  status: number
  data: unknown
}

type AdapterHandler = (config: AxiosRequestConfig) => AdapterResponse | Promise<AdapterResponse>

function installAdapter(
  apiClient: import('axios').AxiosInstance,
  handler: AdapterHandler,
) {
  apiClient.defaults.adapter = async (config) => {
    const result = await handler(config)
    const response = {
      data: result.data,
      status: result.status,
      statusText: '',
      headers: {},
      config,
      request: {},
    }
    if (result.status >= 200 && result.status < 300) {
      return response
    }
    const error = new Error(`Request failed with status ${result.status}`) as Error & {
      response?: typeof response
      config?: typeof config
      isAxiosError?: boolean
    }
    error.response = response
    error.config = config
    error.isAxiosError = true
    throw error
  }
}

describe('apiClient interceptors', () => {
  let apiClient: typeof import('./client').default

  beforeEach(async () => {
    vi.resetModules()
    refreshAccessTokenMock.mockReset()
    clearStateMock.mockReset()
    routerPushMock.mockReset()
    authStoreState.accessToken = 'old-access'
    routerCurrentRoute.value.fullPath = '/library'

    const mod = await import('./client')
    apiClient = mod.default
  })

  it('has request interceptors configured (X-Request-Id + auth)', () => {
    const interceptors = (apiClient.interceptors.request as unknown as { handlers: Array<unknown> }).handlers
    const active = interceptors.filter((h) => h !== null)
    expect(active.length).toBeGreaterThanOrEqual(1)
  })

  it('has response interceptor for 401 handling', () => {
    const interceptors = (apiClient.interceptors.response as unknown as { handlers: Array<unknown> }).handlers
    const active = interceptors.filter((h) => h !== null)
    expect(active.length).toBeGreaterThanOrEqual(1)
  })

  it('401 on protected request triggers authStore.refreshAccessToken and retries the original request', async () => {
    refreshAccessTokenMock.mockImplementation(async () => {
      authStoreState.accessToken = 'new-access'
    })

    let callCount = 0
    let retriedAuthHeader: string | undefined
    installAdapter(apiClient, (config) => {
      callCount += 1
      if (callCount === 1) {
        return { status: 401, data: { message: 'expired' } }
      }
      retriedAuthHeader = config.headers?.['Authorization'] as string | undefined
      return { status: 200, data: { ok: true } }
    })

    const response = await apiClient.get('/protected')

    expect(refreshAccessTokenMock).toHaveBeenCalledTimes(1)
    expect(response.status).toBe(200)
    expect(response.data).toEqual({ ok: true })
    expect(retriedAuthHeader).toBe('Bearer new-access')
    expect(callCount).toBe(2)
  })

  it('refresh failure calls authStore.clearState and router.push to /login with returnUrl (no window.location mutation)', async () => {
    routerCurrentRoute.value.fullPath = '/library/42'
    refreshAccessTokenMock.mockRejectedValue(new Error('refresh expired'))

    installAdapter(apiClient, () => ({ status: 401, data: { message: 'expired' } }))

    const hrefBefore = window.location.href

    await expect(apiClient.get('/protected')).rejects.toBeDefined()

    expect(refreshAccessTokenMock).toHaveBeenCalledTimes(1)
    expect(clearStateMock).toHaveBeenCalledTimes(1)
    expect(routerPushMock).toHaveBeenCalledWith({
      name: 'login',
      query: { returnUrl: '/library/42' },
    })
    expect(window.location.href).toBe(hrefBefore)
  })
})
