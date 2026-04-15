import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseTabs from '../BaseTabs.vue'

const tabs = [
  { key: 'active', label: 'Active' },
  { key: 'queued', label: 'Queued' },
  { key: 'completed', label: 'Completed' },
]

describe('BaseTabs', () => {
  it('renders all tabs and marks active tab correctly', () => {
    const wrapper = mount(BaseTabs, {
      props: { tabs, activeTab: 'queued' },
    })

    const buttons = wrapper.findAll('button[role="tab"]')
    expect(buttons).toHaveLength(3)
    expect(buttons[0].text()).toBe('Active')
    expect(buttons[1].text()).toBe('Queued')
    expect(buttons[2].text()).toBe('Completed')

    expect(buttons[1].attributes('aria-selected')).toBe('true')
    expect(buttons[0].attributes('aria-selected')).toBe('false')
  })

  it('emits update:activeTab when a tab is clicked', async () => {
    const wrapper = mount(BaseTabs, {
      props: { tabs, activeTab: 'active' },
    })

    const buttons = wrapper.findAll('button[role="tab"]')
    await buttons[2].trigger('click')

    expect(wrapper.emitted('update:activeTab')).toEqual([['completed']])
  })
})
