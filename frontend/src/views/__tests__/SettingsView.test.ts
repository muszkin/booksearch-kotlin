import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SettingsView from '../SettingsView.vue'

describe('SettingsView', () => {
  it('renders empty state with settings message', () => {
    const wrapper = mount(SettingsView)

    expect(wrapper.text()).toContain('Settings')
    expect(wrapper.text()).toContain('Configuration options will appear here.')
  })
})
