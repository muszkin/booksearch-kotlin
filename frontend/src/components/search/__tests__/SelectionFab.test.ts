import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SelectionFab from '../SelectionFab.vue'

describe('SelectionFab', () => {
  it('renders with selected count and emits click on tap', async () => {
    const wrapper = mount(SelectionFab, {
      props: { count: 5 },
    })

    expect(wrapper.text()).toContain('5')
    expect(wrapper.text()).toContain('Selected')

    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('click')).toBeTruthy()
  })
})
