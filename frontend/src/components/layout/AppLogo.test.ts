import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppLogo from './AppLogo.vue'

describe('AppLogo', () => {
  it('renders a violet book icon and "BookSearch" text', () => {
    const wrapper = mount(AppLogo)

    expect(wrapper.find('svg').exists()).toBe(true)
    expect(wrapper.text()).toContain('BookSearch')
    expect(wrapper.find('svg').classes()).toContain('text-violet-400')
  })
})
