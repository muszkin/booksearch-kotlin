import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const apiClient = axios.create({
  baseURL: '/api',
})

const AUTH_ENDPOINTS = ['/auth/refresh', '/auth/login', '/auth/register']

let isRefreshing = false
type RefreshSubscriber = (token: string | null) => void
let refreshQueue: RefreshSubscriber[] = []

function subscribeToRefresh(cb: RefreshSubscriber) {
  refreshQueue.push(cb)
}

function broadcastRefreshResult(token: string | null) {
  const queue = refreshQueue
  refreshQueue = []
  queue.forEach((cb) => cb(token))
}

function isAuthEndpoint(url: string | undefined): boolean {
  if (!url) return false
  return AUTH_ENDPOINTS.some((ep) => url.includes(ep))
}

apiClient.interceptors.request.use((config) => {
  config.headers['X-Request-Id'] = crypto.randomUUID()

  const accessToken = localStorage.getItem('accessToken')
  if (accessToken) {
    config.headers['Authorization'] = `Bearer ${accessToken}`
  }

  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    if (
      error.response?.status !== 401 ||
      originalRequest._retry ||
      isAuthEndpoint(originalRequest.url)
    ) {
      return Promise.reject(error)
    }

    originalRequest._retry = true

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        subscribeToRefresh((token) => {
          if (!token) {
            reject(error)
            return
          }
          originalRequest.headers['Authorization'] = `Bearer ${token}`
          resolve(apiClient(originalRequest))
        })
      })
    }

    isRefreshing = true
    const authStore = useAuthStore()

    try {
      await authStore.refreshAccessToken()
      const newToken = authStore.accessToken
      broadcastRefreshResult(newToken)
      originalRequest.headers['Authorization'] = `Bearer ${newToken}`
      return apiClient(originalRequest)
    } catch {
      broadcastRefreshResult(null)
      authStore.clearState()
      await softRedirectToLogin()
      return Promise.reject(error)
    } finally {
      isRefreshing = false
    }
  },
)

async function softRedirectToLogin() {
  if (router.currentRoute.value.name === 'login') {
    return
  }
  await router.push({
    name: 'login',
    query: { returnUrl: router.currentRoute.value.fullPath },
  })
}

export default apiClient
