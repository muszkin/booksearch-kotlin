import { describe, it, expect, afterEach } from 'vitest'
import { mount, VueWrapper } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia } from 'pinia'
import MobileDrawer from './MobileDrawer.vue'

function createTestRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/search', name: 'search', component: { template: '<div />' } },
      { path: '/library', name: 'library', component: { template: '<div />' } },
      { path: '/settings', name: 'settings', component: { template: '<div />' } },
    ],
  })
  router.push('/search')
  return router
}

describe('MobileDrawer', () => {
  let wrapper: VueWrapper

  afterEach(() => {
    wrapper?.unmount()
  })

  it('renders with role="dialog" and aria-modal when open', async () => {
    const router = createTestRouter()
    await router.isReady()

    wrapper = mount(MobileDrawer, {
      props: { open: true },
      global: {
        plugins: [router, createPinia()],
        stubs: { Teleport: true },
      },
    })

    const dialog = wrapper.find('[role="dialog"]')
    expect(dialog.exists()).toBe(true)
    expect(dialog.attributes('aria-modal')).toBe('true')
  })

  it('emits close event when overlay is clicked', async () => {
    const router = createTestRouter()
    await router.isReady()

    wrapper = mount(MobileDrawer, {
      props: { open: true },
      global: {
        plugins: [router, createPinia()],
        stubs: { Teleport: true },
      },
    })

    await wrapper.find('[data-testid="drawer-overlay"]').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('emits close event when Escape key is pressed', async () => {
    const router = createTestRouter()
    await router.isReady()

    wrapper = mount(MobileDrawer, {
      props: { open: true },
      global: {
        plugins: [router, createPinia()],
        stubs: { Teleport: true },
      },
    })

    await wrapper.find('[role="dialog"]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('does not render dialog content when closed', async () => {
    const router = createTestRouter()
    await router.isReady()

    wrapper = mount(MobileDrawer, {
      props: { open: false },
      global: {
        plugins: [router, createPinia()],
        stubs: { Teleport: true },
      },
    })

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})
