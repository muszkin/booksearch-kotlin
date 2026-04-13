import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PageHeader from './PageHeader.vue'

describe('PageHeader', () => {
  it('renders the title text and applies sticky positioning', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Search Results' },
    })

    expect(wrapper.text()).toContain('Search Results')

    const header = wrapper.find('header')
    expect(header.exists()).toBe(true)
    expect(header.classes()).toContain('sticky')
    expect(header.classes()).toContain('top-0')
  })
})
