import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import SmtpConfigCard from './SmtpConfigCard.vue'
import type { DeviceSettingsResponse } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    SettingsService: {
      getAllSettings: vi.fn(),
      saveDeviceSettings: vi.fn(),
      deleteDeviceSettings: vi.fn(),
    },
  }
})

const mockSettings: DeviceSettingsResponse = {
  host: 'smtp.gmail.com',
  port: '587',
  username: 'user@gmail.com',
  password: '********',
  fromEmail: 'user@gmail.com',
  recipientEmail: 'kindle@kindle.com',
}

describe('SmtpConfigCard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders all 6 SMTP fields when device prop is provided', () => {
    const wrapper = mount(SmtpConfigCard, {
      props: { device: 'kindle', title: 'Kindle' },
    })

    const labels = wrapper.findAll('label')
    const labelTexts = labels.map((l) => l.text())

    expect(labelTexts).toContain('SMTP Host')
    expect(labelTexts).toContain('SMTP Port')
    expect(labelTexts).toContain('Username')
    expect(labelTexts).toContain('Password')
    expect(labelTexts).toContain('From Email')
    expect(labelTexts).toContain('Recipient Email')
  })

  it('shows emerald "Configured" badge when device has settings, zinc "Not configured" when empty', async () => {
    const { useSettingsStore } = await import('@/stores/settings')

    const wrapper = mount(SmtpConfigCard, {
      props: { device: 'kindle', title: 'Kindle' },
    })

    const badge = wrapper.find('[data-testid="status-badge"]')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toContain('Not configured')
    expect(badge.classes().some((c) => c.includes('zinc'))).toBe(true)

    const store = useSettingsStore()
    store.deviceSettings.kindle = mockSettings

    await wrapper.vm.$nextTick()

    const updatedBadge = wrapper.find('[data-testid="status-badge"]')
    expect(updatedBadge.text()).toContain('Configured')
    expect(updatedBadge.classes().some((c) => c.includes('emerald'))).toBe(true)
  })
})
