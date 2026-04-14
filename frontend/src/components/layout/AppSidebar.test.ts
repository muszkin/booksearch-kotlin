import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import AppSidebar from './AppSidebar.vue'
import { useAuthStore } from '@/stores/auth'

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
})
