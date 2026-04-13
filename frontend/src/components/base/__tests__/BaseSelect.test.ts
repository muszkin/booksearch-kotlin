import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseSelect from '../BaseSelect.vue'

describe('BaseSelect', () => {
  const defaultOptions = [
    { value: 'pl', label: 'PL' },
    { value: 'en', label: 'EN' },
    { value: 'de', label: 'DE' },
  ]

  it('renders options from the options prop', () => {
    const wrapper = mount(BaseSelect, {
      props: { modelValue: 'pl', options: defaultOptions },
    })

    const options = wrapper.findAll('option')
    expect(options).toHaveLength(3)
    expect(options[0].text()).toBe('PL')
    expect(options[0].attributes('value')).toBe('pl')
    expect(options[1].text()).toBe('EN')
    expect(options[2].text()).toBe('DE')
  })

  it('emits update:modelValue when selection changes', async () => {
    const wrapper = mount(BaseSelect, {
      props: { modelValue: 'pl', options: defaultOptions },
    })

    await wrapper.find('select').setValue('en')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['en'])
  })

  it('applies dark theme styling with proper touch target', () => {
    const wrapper = mount(BaseSelect, {
      props: { modelValue: 'pl', options: defaultOptions },
    })

    const select = wrapper.find('select')
    expect(select.classes()).toContain('bg-zinc-800')
    expect(select.classes()).toContain('border-zinc-600')
    expect(select.classes()).toContain('min-h-[44px]')
  })

  it('renders label associated with select when provided', () => {
    const wrapper = mount(BaseSelect, {
      props: { modelValue: 'pl', options: defaultOptions, label: 'Language' },
    })

    const label = wrapper.find('label')
    const select = wrapper.find('select')
    expect(label.text()).toBe('Language')
    expect(label.attributes('for')).toBe(select.attributes('id'))
  })
})
