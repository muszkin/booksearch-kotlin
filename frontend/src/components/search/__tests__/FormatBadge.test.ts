import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import FormatBadge from '../FormatBadge.vue'

describe('FormatBadge', () => {
  it('renders format string with neutral rounded styling', () => {
    const wrapper = mount(FormatBadge, {
      props: { format: 'epub' },
    })

    const badge = wrapper.find('span')
    expect(badge.text()).toBe('epub')
    expect(badge.classes()).toContain('bg-zinc-700')
    expect(badge.classes()).toContain('text-zinc-300')
    expect(badge.classes()).toContain('text-xs')
    expect(badge.classes()).toContain('rounded')
    expect(badge.classes()).toContain('uppercase')
  })
})
