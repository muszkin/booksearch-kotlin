import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SearchView from '../SearchView.vue'

describe('SearchView', () => {
  it('renders empty state with search prompt', () => {
    const wrapper = mount(SearchView)

    expect(wrapper.text()).toContain('Search for books')
    expect(wrapper.text()).toContain('Use the search bar to find your next great read.')
  })
})
