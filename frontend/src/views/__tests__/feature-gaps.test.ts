import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useSettingsStore } from '@/stores/settings'
import { useAdminStore } from '@/stores/admin'
import { setupRouteGuards } from '@/router/guards'
import ChangePasswordForm from '@/components/settings/ChangePasswordForm.vue'
import ChangeUserPasswordModal from '@/components/admin/ChangeUserPasswordModal.vue'
import UserTable from '@/components/admin/UserTable.vue'
import InviteUserForm from '@/components/admin/InviteUserForm.vue'
import { SettingsService, AdminService, AuthService, CancelablePromise } from '@/api/generated'
import type { UserResponse } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    SettingsService: {
      getAllSettings: vi.fn(),
      saveDeviceSettings: vi.fn(),
      deleteDeviceSettings: vi.fn(),
    },
    AdminService: {
      listUsers: vi.fn(),
      toggleRegistration: vi.fn(),
      createUser: vi.fn(),
      changeUserPassword: vi.fn(),
    },
    AuthService: {
      changeOwnPassword: vi.fn(),
    },
  }
})

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

function rejecting<T = any>(message: string): CancelablePromise<T> {
  return new CancelablePromise<T>((_resolve, reject) => reject(new Error(message)))
}

function resolving<T>(value: T): CancelablePromise<T> {
  return new CancelablePromise((resolve) => resolve(value))
}

const mockUser: UserResponse = {
  id: 1,
  email: 'admin@example.com',
  displayName: 'Admin',
  isSuperAdmin: true,
  isActive: true,
  forcePasswordChange: false,
  createdAt: '2026-01-01T00:00:00Z',
}

describe('Feature Gap Tests', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('Settings store error handling', () => {
    it('fetchSettings sets error state when API call fails', async () => {
      vi.mocked(SettingsService.getAllSettings).mockReturnValue(rejecting('Network error'))

      const store = useSettingsStore()
      await store.fetchSettings()

      expect(store.error).toBe('Network error')
      expect(store.loading).toBe(false)
      expect(store.deviceSettings.kindle).toBeNull()
    })
  })

  describe('Admin store error handling', () => {
    it('fetchUsers sets error and clears users when API call fails', async () => {
      vi.mocked(AdminService.listUsers).mockReturnValue(rejecting('Forbidden'))

      const store = useAdminStore()
      await store.fetchUsers()

      expect(store.error).toBe('Forbidden')
      expect(store.users).toHaveLength(0)
      expect(store.loading).toBe(false)
    })
  })

  describe('ChangePasswordForm error display', () => {
    it('shows API error message when changeOwnPassword fails', async () => {
      vi.mocked(AuthService.changeOwnPassword).mockReturnValue(rejecting('Invalid current password'))

      const wrapper = mount(ChangePasswordForm)
      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('wrongpassword')
      await inputs[1].setValue('newpassword1')
      await inputs[2].setValue('newpassword1')

      await wrapper.find('form').trigger('submit')
      await flushPromises()

      expect(wrapper.text()).toContain('Invalid current password')
    })
  })

  describe('Route guard edge case', () => {
    it('redirects to settings when user is null and route requires super admin', async () => {
      const router = createRouter({
        history: createMemoryHistory(),
        routes: [
          { path: '/login', name: 'login', meta: { guest: true }, component: { template: '<div>Login</div>' } },
          { path: '/search', name: 'search', meta: { requiresAuth: true }, component: { template: '<div>Search</div>' } },
          { path: '/settings', name: 'settings', meta: { requiresAuth: true }, component: { template: '<div>Settings</div>' } },
          { path: '/admin', name: 'admin', meta: { requiresAuth: true, requiresSuperAdmin: true }, component: { template: '<div>Admin</div>' } },
        ],
      })
      setupRouteGuards(router)

      const authStore = useAuthStore()
      authStore.restoreSession = vi.fn()
      authStore.accessToken = 'valid-token'
      authStore.user = null
      authStore.loadCurrentUser = vi.fn(async () => {
        authStore.user = {
          id: 2,
          email: 'user@example.com',
          displayName: 'User',
          isSuperAdmin: false,
          isActive: true,
          forcePasswordChange: false,
          createdAt: '2026-01-01T00:00:00Z',
        }
      })

      await router.push('/admin')
      await router.isReady()

      expect(router.currentRoute.value.name).toBe('settings')
    })
  })

  describe('UserTable empty state', () => {
    it('shows empty state when users array is empty', () => {
      const wrapper = mount(UserTable, {
        props: { users: [] },
      })

      expect(wrapper.text()).toContain('No users')
    })
  })

  describe('ChangeUserPasswordModal validation', () => {
    it('shows validation error for password shorter than 8 characters', async () => {
      vi.mocked(AdminService.changeUserPassword).mockReturnValue(resolving({}))

      const wrapper = mount(ChangeUserPasswordModal, {
        props: { user: mockUser, visible: true },
        global: { stubs: { Teleport: true } },
      })

      const passwordInput = wrapper.find('input[type="password"]')
      await passwordInput.setValue('short')

      await wrapper.find('form').trigger('submit')
      await flushPromises()

      expect(wrapper.text()).toContain('at least 8 characters')
      expect(AdminService.changeUserPassword).not.toHaveBeenCalled()
    })
  })

  describe('InviteUserForm email validation', () => {
    it('shows validation error for invalid email format', async () => {
      const wrapper = mount(InviteUserForm)

      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('not-an-email')
      await inputs[1].setValue('Test User')
      await inputs[2].setValue('password123')

      await wrapper.find('form').trigger('submit')

      expect(wrapper.text()).toContain('Invalid email format')
    })
  })

  describe('Admin store toggleRegistration error handling', () => {
    it('sets error when toggleRegistration API call fails', async () => {
      vi.mocked(AdminService.toggleRegistration).mockReturnValue(rejecting('Server error'))

      const store = useAdminStore()
      store.registrationEnabled = false

      await store.toggleRegistration(true)

      expect(store.error).toBe('Server error')
      expect(store.registrationEnabled).toBe(false)
    })
  })
})
