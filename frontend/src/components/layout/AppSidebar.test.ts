import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import AppSidebar from './AppSidebar.vue'
import { useAuthStore } from '@/stores/auth'
import apiClient from '@/api/client'

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

function createTestRouter(currentPath = '/search') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/search', name: 'search', component: { template: '<div />' } },
      { path: '/library', name: 'library', component: { template: '<div />' } },
      { path: '/settings', name: 'settings', component: { template: '<div />' } },
      { path: '/admin', name: 'admin', component: { template: '<div />' } },
    ],
  })
  router.push(currentPath)
  return router
}

describe('AppSidebar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders nav element with Search, Library, and Settings links', async () => {
    const router = createTestRouter()
    await router.isReady()

    const wrapper = mount(AppSidebar, {
      global: { plugins: [router] },
    })

    const nav = wrapper.find('nav')
    expect(nav.exists()).toBe(true)

    const links = wrapper.findAll('a')
    const linkTexts = links.map((l) => l.text())
    expect(linkTexts).toContain('Search')
    expect(linkTexts).toContain('Library')
    expect(linkTexts).toContain('Settings')
  })

  it('marks the active route with aria-current="page"', async () => {
    const router = createTestRouter('/library')
    await router.isReady()

    const wrapper = mount(AppSidebar, {
      global: { plugins: [router] },
    })

    const activeLink = wrapper.find('a[aria-current="page"]')
    expect(activeLink.exists()).toBe(true)
    expect(activeLink.text()).toBe('Library')
  })

  it('shows Admin nav item when user isSuperAdmin is true', async () => {
    const router = createTestRouter()
    await router.isReady()

    const authStore = useAuthStore()
    authStore.user = {
      id: 1,
      email: 'admin@example.com',
      displayName: 'Admin',
      isSuperAdmin: true,
      isActive: true,
      forcePasswordChange: false,
      createdAt: '2026-01-01T00:00:00Z',
    }

    const wrapper = mount(AppSidebar, {
      global: { plugins: [router] },
    })

    const links = wrapper.findAll('a')
    const linkTexts = links.map((l) => l.text())
    expect(linkTexts).toContain('Admin')
  })

  it('hides Admin nav item when user isSuperAdmin is false', async () => {
    const router = createTestRouter()
    await router.isReady()

    const authStore = useAuthStore()
    authStore.user = {
      id: 2,
      email: 'user@example.com',
      displayName: 'User',
      isSuperAdmin: false,
      isActive: true,
      forcePasswordChange: false,
      createdAt: '2026-01-01T00:00:00Z',
    }

    const wrapper = mount(AppSidebar, {
      global: { plugins: [router] },
    })

    const links = wrapper.findAll('a')
    const linkTexts = links.map((l) => l.text())
    expect(linkTexts).not.toContain('Admin')
  })

  it('shows the return link above the impersonated user and restores the admin session', async () => {
    const router = createTestRouter()
    await router.isReady()

    const authStore = useAuthStore()
    authStore.accessToken = 'impersonation-access'
    authStore.refreshToken = 'impersonation-refresh'
    authStore.user = {
      id: 3,
      email: 'jus.mar@example.com',
      displayName: 'Justyna Marciniak',
      isSuperAdmin: false,
      isActive: true,
      forcePasswordChange: false,
      createdAt: '2026-01-01T00:00:00Z',
      actAsUserId: 1,
      actAsEmail: 'admin@example.com',
    }
    ;(apiClient.post as Mock).mockResolvedValueOnce({
      data: {
        accessToken: 'admin-access',
        refreshToken: 'admin-refresh',
        user: {
          id: 1,
          email: 'admin@example.com',
          displayName: 'Admin',
          isSuperAdmin: true,
          isActive: true,
          forcePasswordChange: false,
          createdAt: '2026-01-01T00:00:00Z',
        },
      },
    })

    const wrapper = mount(AppSidebar, {
      global: { plugins: [router] },
    })

    const returnLink = wrapper.find('[data-testid="return-to-admin-link"]')
    expect(returnLink.exists()).toBe(true)
    expect(returnLink.text()).toBe('Return to admin')
    expect(wrapper.html().indexOf('return-to-admin-link'))
      .toBeLessThan(wrapper.html().indexOf('Justyna Marciniak'))

    await returnLink.trigger('click')
    await flushPromises()

    expect(apiClient.post).toHaveBeenCalledWith('/admin/impersonate/stop', {
      refreshToken: 'impersonation-refresh',
    })
    expect(authStore.isImpersonating).toBe(false)
    expect(router.currentRoute.value.path).toBe('/admin')
  })
})
