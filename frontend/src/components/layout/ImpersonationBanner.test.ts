import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory, type Router } from 'vue-router'
import ImpersonationBanner from './ImpersonationBanner.vue'
import { useAuthStore } from '@/stores/auth'
import type { UserResponse } from '@/api/generated'

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

const impersonatedUser: UserResponse = {
  id: 42,
  email: 'jan@x.pl',
  displayName: 'Jan',
  isSuperAdmin: false,
  isActive: true,
  forcePasswordChange: false,
  createdAt: '2026-02-01T00:00:00Z',
  actAsUserId: 1,
  actAsEmail: 'admin@x.pl',
}

describe('ImpersonationBanner', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders user info + Return to admin button when impersonating', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()

    const authStore = useAuthStore()
    authStore.user = { ...impersonatedUser }

    const wrapper = mount(ImpersonationBanner, {
      global: { plugins: [router] },
    })

    const html = wrapper.html()
    expect(html).toContain('Jan')
    expect(html).toContain('jan@x.pl')
    expect(wrapper.text()).toContain('Return to admin')

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    // AlertMessage warning variant uses amber palette.
    expect(alert.classes().some((c) => c.includes('amber'))).toBe(true)
  })

  it('clicking Return to admin calls authStore.stopImpersonation and router.push("/admin")', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()
    const pushSpy = vi.spyOn(router, 'push')

    const authStore = useAuthStore()
    authStore.user = { ...impersonatedUser }
    authStore.stopImpersonation = vi.fn().mockResolvedValue(undefined)

    const wrapper = mount(ImpersonationBanner, {
      global: { plugins: [router] },
    })

    const button = wrapper.find('[data-testid="return-to-admin-btn"]')
    expect(button.exists()).toBe(true)

    await button.trigger('click')
    await flushPromises()

    expect(authStore.stopImpersonation).toHaveBeenCalledTimes(1)
    expect(pushSpy).toHaveBeenCalledWith('/admin')
  })
})
