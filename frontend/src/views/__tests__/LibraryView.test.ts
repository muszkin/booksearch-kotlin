import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import LibraryView from '../LibraryView.vue'

describe('LibraryView', () => {
  it('renders empty state with library message and CTA linking to search', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: LibraryView },
        { path: '/search', name: 'search', component: { template: '<div />' } },
      ],
    })

    await router.push('/')
    await router.isReady()

    const wrapper = mount(LibraryView, {
      global: {
        plugins: [router],
      },
    })

    expect(wrapper.text()).toContain('Your library is empty')
    expect(wrapper.text()).toContain('Books you download will appear here.')

    const link = wrapper.find('a')
    expect(link.exists()).toBe(true)
    expect(link.attributes('href')).toBe('/search')
  })
})
