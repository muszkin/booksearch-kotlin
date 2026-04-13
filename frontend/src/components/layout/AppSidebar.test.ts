import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia } from 'pinia'
import AppSidebar from './AppSidebar.vue'

function createTestRouter(currentPath = '/search') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/search', name: 'search', component: { template: '<div />' } },
      { path: '/library', name: 'library', component: { template: '<div />' } },
      { path: '/settings', name: 'settings', component: { template: '<div />' } },
    ],
  })
  router.push(currentPath)
  return router
}

describe('AppSidebar', () => {
  it('renders nav element with Search, Library, and Settings links', async () => {
    const router = createTestRouter()
    await router.isReady()

    const wrapper = mount(AppSidebar, {
      global: { plugins: [router, createPinia()] },
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
      global: { plugins: [router, createPinia()] },
    })

    const activeLink = wrapper.find('a[aria-current="page"]')
    expect(activeLink.exists()).toBe(true)
    expect(activeLink.text()).toBe('Library')
  })
})
