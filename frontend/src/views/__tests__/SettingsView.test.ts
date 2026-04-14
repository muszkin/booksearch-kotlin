import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import SettingsView from '../SettingsView.vue'
import { SettingsService, CancelablePromise } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    SettingsService: {
      getAllSettings: vi.fn(),
      saveDeviceSettings: vi.fn(),
      deleteDeviceSettings: vi.fn(),
    },
    AuthService: {
      changeOwnPassword: vi.fn(),
    },
  }
})

function resolving<T>(value: T): CancelablePromise<T> {
  return new CancelablePromise((resolve) => resolve(value))
}

describe('SettingsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(SettingsService.getAllSettings).mockReturnValue(resolving({}))
  })

  it('renders PageHeader with title "Settings"', () => {
    const wrapper = mount(SettingsView)
    const header = wrapper.find('header')
    expect(header.exists()).toBe(true)
    expect(header.text()).toContain('Settings')
  })

  it('renders two SmtpConfigCard instances (kindle + pocketbook)', () => {
    const wrapper = mount(SettingsView)
    const cards = wrapper.findAll('[data-testid="status-badge"]')
    expect(cards).toHaveLength(2)
    expect(wrapper.text()).toContain('Kindle')
    expect(wrapper.text()).toContain('PocketBook')
  })

  it('renders ChangePasswordForm section', () => {
    const wrapper = mount(SettingsView)
    expect(wrapper.text()).toContain('Change Password')
    expect(wrapper.text()).toContain('Security')
  })

  it('calls settings store fetchSettings on mount', async () => {
    mount(SettingsView)
    await flushPromises()
    expect(SettingsService.getAllSettings).toHaveBeenCalledOnce()
  })
})
