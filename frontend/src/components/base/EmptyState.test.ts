import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EmptyState from './EmptyState.vue'

describe('EmptyState', () => {
  it('renders title and description', () => {
    const wrapper = mount(EmptyState, {
      props: {
        title: 'No results',
        description: 'Try a different search query',
      },
    })

    expect(wrapper.text()).toContain('No results')
    expect(wrapper.text()).toContain('Try a different search query')
  })

  it('renders icon slot when provided', () => {
    const wrapper = mount(EmptyState, {
      props: { title: 'Empty' },
      slots: { icon: '<svg data-testid="custom-icon"></svg>' },
    })

    expect(wrapper.find('[data-testid="custom-icon"]').exists()).toBe(true)
  })

  it('renders action slot for CTA button', () => {
    const wrapper = mount(EmptyState, {
      props: { title: 'No books' },
      slots: { action: '<button>Search again</button>' },
    })

    expect(wrapper.find('button').text()).toBe('Search again')
  })
})
