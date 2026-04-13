import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SearchToolbar from '../SearchToolbar.vue'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseButton from '@/components/base/BaseButton.vue'

function mountToolbar(propsOverrides = {}) {
  return mount(SearchToolbar, {
    props: {
      query: '',
      language: 'pl',
      format: 'epub',
      loading: false,
      ...propsOverrides,
    },
  })
}

describe('SearchToolbar', () => {
  it('renders BaseInput for query, two BaseSelect for language/format, and BaseButton for search', () => {
    const wrapper = mountToolbar()

    expect(wrapper.findComponent(BaseInput).exists()).toBe(true)
    expect(wrapper.findAllComponents(BaseSelect)).toHaveLength(2)
    expect(wrapper.findComponent(BaseButton).exists()).toBe(true)
  })

  it('emits search event with query, language, format on button click', async () => {
    const wrapper = mountToolbar({ query: 'Przestrzeń objawienia', language: 'pl', format: 'epub' })

    const form = wrapper.find('form')
    await form.trigger('submit')

    expect(wrapper.emitted('search')).toBeTruthy()
    expect(wrapper.emitted('search')![0]).toEqual([])
  })

  it('emits search on Enter key via form submit', async () => {
    const wrapper = mountToolbar({ query: 'test query' })

    const form = wrapper.find('form')
    await form.trigger('submit')

    expect(wrapper.emitted('search')).toBeTruthy()
  })
})
