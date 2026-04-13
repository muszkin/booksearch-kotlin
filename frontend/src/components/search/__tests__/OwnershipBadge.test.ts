import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import OwnershipBadge from '../OwnershipBadge.vue'
import FormatBadge from '../FormatBadge.vue'

describe('OwnershipBadge', () => {
  it('renders correct label and color per matchType', () => {
    const cases = [
      { matchType: 'exact', label: 'OWNED', colorClass: 'text-emerald-300' },
      { matchType: 'title', label: 'SAME TITLE', colorClass: 'text-amber-300' },
      { matchType: 'author', label: 'SAME AUTHOR', colorClass: 'text-sky-300' },
    ] as const

    for (const { matchType, label, colorClass } of cases) {
      const wrapper = mount(OwnershipBadge, {
        props: { matchType },
      })

      expect(wrapper.text()).toContain(label)
      expect(wrapper.find('span').classes()).toContain(colorClass)
    }
  })

  it('renders nothing when matchType is none', () => {
    const wrapper = mount(OwnershipBadge, {
      props: { matchType: 'none' },
    })

    expect(wrapper.html()).toBe('<!--v-if-->')
  })

  it('shows owned format badges when matchType is exact and ownedFormats provided', () => {
    const wrapper = mount(OwnershipBadge, {
      props: { matchType: 'exact', ownedFormats: ['epub', 'pdf'] },
    })

    const formatBadges = wrapper.findAllComponents(FormatBadge)
    expect(formatBadges).toHaveLength(2)
    expect(formatBadges[0].props('format')).toBe('epub')
    expect(formatBadges[1].props('format')).toBe('pdf')
  })
})
