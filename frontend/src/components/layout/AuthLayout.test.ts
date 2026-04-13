import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AuthLayout from './AuthLayout.vue'

describe('AuthLayout', () => {
  it('renders AppLogo and slot content within a centered card', () => {
    const wrapper = mount(AuthLayout, {
      slots: { default: '<p data-testid="slot-content">Form goes here</p>' },
    })

    expect(wrapper.text()).toContain('BookSearch')
    expect(wrapper.find('[data-testid="slot-content"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="slot-content"]').text()).toBe('Form goes here')
  })

  it('has responsive centering classes for mobile and desktop', () => {
    const wrapper = mount(AuthLayout, {
      slots: { default: '<div>Content</div>' },
    })

    const outerDiv = wrapper.find('div')
    expect(outerDiv.classes()).toContain('flex')
    expect(outerDiv.classes()).toContain('min-h-screen')
    expect(outerDiv.classes()).toContain('items-center')
    expect(outerDiv.classes()).toContain('justify-center')
  })
})
