import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseButton from './BaseButton.vue'

describe('BaseButton', () => {
  it('renders as a button element with default primary variant', () => {
    const wrapper = mount(BaseButton, {
      slots: { default: 'Click me' },
    })

    const button = wrapper.find('button')
    expect(button.exists()).toBe(true)
    expect(button.text()).toBe('Click me')
    expect(button.classes()).toContain('bg-accent')
  })

  it('applies correct classes for each variant', () => {
    const variants = ['primary', 'secondary', 'danger', 'ghost'] as const
    const expectedClasses = {
      primary: 'bg-accent',
      secondary: 'bg-surface',
      danger: 'bg-rose-500',
      ghost: 'bg-transparent',
    }

    for (const variant of variants) {
      const wrapper = mount(BaseButton, {
        props: { variant },
        slots: { default: 'Button' },
      })
      expect(wrapper.find('button').classes()).toContain(expectedClasses[variant])
    }
  })

  it('shows loading spinner and disables button when loading', () => {
    const wrapper = mount(BaseButton, {
      props: { loading: true },
      slots: { default: 'Submit' },
    })

    const button = wrapper.find('button')
    expect(button.attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="loading-spinner"]').exists()).toBe(true)
  })

  it('disables button when disabled prop is true', () => {
    const wrapper = mount(BaseButton, {
      props: { disabled: true },
      slots: { default: 'Disabled' },
    })

    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
  })
})
