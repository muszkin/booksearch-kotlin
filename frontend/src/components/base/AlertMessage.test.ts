import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AlertMessage from './AlertMessage.vue'

describe('AlertMessage', () => {
  it('renders message with appropriate role attribute', () => {
    const wrapper = mount(AlertMessage, {
      props: { variant: 'error', message: 'Something went wrong' },
    })

    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Something went wrong')
  })

  it('applies correct styling for each variant', () => {
    const variants = ['success', 'error', 'warning', 'info'] as const
    const expectedClasses = {
      success: 'text-emerald-300',
      error: 'text-rose-300',
      warning: 'text-amber-300',
      info: 'text-sky-300',
    }

    for (const variant of variants) {
      const wrapper = mount(AlertMessage, {
        props: { variant, message: 'Test message' },
      })
      expect(wrapper.find('[role="alert"]').classes()).toContain(expectedClasses[variant])
    }
  })

  it('renders slot content when provided instead of message prop', () => {
    const wrapper = mount(AlertMessage, {
      props: { variant: 'info' },
      slots: { default: '<strong>Custom content</strong>' },
    })

    expect(wrapper.find('strong').exists()).toBe(true)
    expect(wrapper.text()).toContain('Custom content')
  })
})
