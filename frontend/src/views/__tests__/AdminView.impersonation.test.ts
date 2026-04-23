import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory, type Router } from 'vue-router'
import AdminView from '../AdminView.vue'
import { AdminService, CancelablePromise } from '@/api/generated'
import type { UserResponse } from '@/api/generated'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    AdminService: {
      listUsers: vi.fn(),
      toggleRegistration: vi.fn(),
      createUser: vi.fn(),
      changeUserPassword: vi.fn(),
    },
  }
})

function resolving<T>(value: T): CancelablePromise<T> {
  return new CancelablePromise((resolve) => resolve(value))
}

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/admin', name: 'admin', component: { template: '<div />' } },
      { path: '/search', name: 'search', component: { template: '<div />' } },
    ],
  })
}

const adminUser: UserResponse = {
  id: 1,
  email: 'admin@example.com',
  displayName: 'Admin',
  isSuperAdmin: true,
  isActive: true,
  forcePasswordChange: false,
  createdAt: '2026-01-01T00:00:00Z',
}

const regularUser: UserResponse = {
  id: 2,
  email: 'user@example.com',
  displayName: 'Regular User',
  isSuperAdmin: false,
  isActive: true,
  forcePasswordChange: false,
  createdAt: '2026-01-02T00:00:00Z',
}

describe('AdminView — impersonation integration', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(AdminService.listUsers).mockReturnValue(resolving([adminUser, regularUser]))
    vi.mocked(AdminService.toggleRegistration).mockReturnValue(resolving({}))
  })

  it('clicking Impersonate on user row calls store.startImpersonation and pushes /search', async () => {
    const router = createTestRouter()
    await router.push('/admin')
    await router.isReady()
    const pushSpy = vi.spyOn(router, 'push')

    const authStore = useAuthStore()
    authStore.user = { ...adminUser }
    authStore.startImpersonation = vi.fn().mockResolvedValue(undefined)

    const wrapper = mount(AdminView, {
      global: { plugins: [router] },
    })
    await flushPromises()

    const impersonateButtons = wrapper.findAll('[data-testid="impersonate-btn"]')
    expect(impersonateButtons.length).toBe(1)

    await impersonateButtons[0].trigger('click')
    await flushPromises()

    expect(authStore.startImpersonation).toHaveBeenCalledTimes(1)
    expect(authStore.startImpersonation).toHaveBeenCalledWith(regularUser.id)
    expect(pushSpy).toHaveBeenCalledWith('/search')
  })
})
