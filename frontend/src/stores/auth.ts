import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import apiClient from '@/api/client'
import type { LoginRequest, LoginResponse, RegisterRequest, UserResponse } from '@/api/generated'
import type { RefreshResponse } from '@/api/generated'

const ACCESS_TOKEN_KEY = 'accessToken'
const REFRESH_TOKEN_KEY = 'refreshToken'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const user = ref<UserResponse | null>(null)

  const isAuthenticated = computed(() => accessToken.value !== null)

  function storeTokens(access: string, refresh: string) {
    accessToken.value = access
    refreshToken.value = refresh
    localStorage.setItem(ACCESS_TOKEN_KEY, access)
    localStorage.setItem(REFRESH_TOKEN_KEY, refresh)
  }

  function clearState() {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  }

  async function login(credentials: LoginRequest) {
    const { data } = await apiClient.post<LoginResponse>('/auth/login', credentials)
    storeTokens(data.accessToken, data.refreshToken)
    user.value = data.user
  }

  async function register(payload: RegisterRequest) {
    const { data } = await apiClient.post<LoginResponse>('/auth/register', payload)
    storeTokens(data.accessToken, data.refreshToken)
    user.value = data.user
  }

  async function logout() {
    try {
      await apiClient.post('/auth/logout', { refreshToken: refreshToken.value })
    } finally {
      clearState()
    }
  }

  async function refreshAccessToken() {
    const { data } = await apiClient.post<RefreshResponse>('/auth/refresh', {
      refreshToken: refreshToken.value,
    })
    accessToken.value = data.accessToken
    localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken)
  }

  function restoreSession() {
    const storedAccess = localStorage.getItem(ACCESS_TOKEN_KEY)
    const storedRefresh = localStorage.getItem(REFRESH_TOKEN_KEY)
    if (storedAccess && storedRefresh) {
      accessToken.value = storedAccess
      refreshToken.value = storedRefresh
    }
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    login,
    register,
    logout,
    refreshAccessToken,
    restoreSession,
  }
})
