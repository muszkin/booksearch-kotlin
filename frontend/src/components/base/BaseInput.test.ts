import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseInput from './BaseInput.vue'

describe('BaseInput', () => {
  it('renders label associated with input via for/id', () => {
    const wrapper = mount(BaseInput, {
      props: { label: 'Email', modelValue: '' },
    })

    const label = wrapper.find('label')
    const input = wrapper.find('input')
    expect(label.exists()).toBe(true)
    expect(label.text()).toBe('Email')
    expect(label.attributes('for')).toBe(input.attributes('id'))
  })

  it('displays error message and applies error styling', () => {
    const wrapper = mount(BaseInput, {
      props: { label: 'Email', modelValue: '', error: 'Invalid email' },
    })

    expect(wrapper.text()).toContain('Invalid email')
    expect(wrapper.find('input').classes()).toContain('border-rose-400')
  })

  it('toggles password visibility when toggle button is clicked', async () => {
    const wrapper = mount(BaseInput, {
      props: { label: 'Password', modelValue: '', type: 'password' },
    })

    const input = wrapper.find('input')
    expect(input.attributes('type')).toBe('password')

    await wrapper.find('[data-testid="password-toggle"]').trigger('click')
    expect(wrapper.find('input').attributes('type')).toBe('text')

    await wrapper.find('[data-testid="password-toggle"]').trigger('click')
    expect(wrapper.find('input').attributes('type')).toBe('password')
  })

  it('emits update:modelValue on input', async () => {
    const wrapper = mount(BaseInput, {
      props: { label: 'Name', modelValue: '' },
    })

    await wrapper.find('input').setValue('John')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['John'])
  })
})
