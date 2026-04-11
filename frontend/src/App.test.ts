import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import HomeView from './views/HomeView.vue'

describe('App', () => {
  it('renders router-view that displays routed components', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [{ path: '/', component: HomeView }],
    })

    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [router],
      },
    })

    expect(wrapper.html()).toContain('BookSearch v2')
  })
})
