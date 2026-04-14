import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSettingsStore } from './settings'
import { SettingsService, CancelablePromise } from '@/api/generated'
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

function resolving<T>(value: T): CancelablePromise<T> {
  return new CancelablePromise((resolve) => resolve(value))
}

const mockKindleSettings: DeviceSettingsResponse = {
  host: 'smtp.gmail.com',
  port: '587',
  username: 'user@gmail.com',
  password: '********',
  fromEmail: 'user@gmail.com',
  recipientEmail: 'kindle@kindle.com',
}

const mockAllSettings: Record<string, DeviceSettingsResponse> = {
  kindle: mockKindleSettings,
}

describe('useSettingsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchSettings calls SettingsService.getAllSettings and populates device state', async () => {
    vi.mocked(SettingsService.getAllSettings).mockReturnValue(resolving(mockAllSettings))

    const store = useSettingsStore()
    await store.fetchSettings()

    expect(SettingsService.getAllSettings).toHaveBeenCalledOnce()
    expect(store.deviceSettings.kindle).toEqual(mockKindleSettings)
    expect(store.deviceSettings.pocketbook).toBeNull()
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('saveSettings calls SettingsService.saveDeviceSettings and updates store', async () => {
    vi.mocked(SettingsService.saveDeviceSettings).mockReturnValue(resolving({}))
    vi.mocked(SettingsService.getAllSettings).mockReturnValue(resolving(mockAllSettings))

    const store = useSettingsStore()
    const data = {
      host: 'smtp.gmail.com',
      port: 587,
      username: 'user@gmail.com',
      password: 'secret',
      fromEmail: 'user@gmail.com',
      recipientEmail: 'kindle@kindle.com',
    }

    await store.saveSettings('kindle', data)

    expect(SettingsService.saveDeviceSettings).toHaveBeenCalledWith('kindle', data)
    expect(SettingsService.getAllSettings).toHaveBeenCalled()
    expect(store.saving).toBe(false)
  })

  it('deleteSettings calls SettingsService.deleteDeviceSettings and clears device entry', async () => {
    vi.mocked(SettingsService.getAllSettings).mockReturnValue(resolving(mockAllSettings))
    vi.mocked(SettingsService.deleteDeviceSettings).mockReturnValue(resolving({}))

    const store = useSettingsStore()
    await store.fetchSettings()
    expect(store.deviceSettings.kindle).not.toBeNull()

    await store.deleteSettings('pocketbook')

    expect(SettingsService.deleteDeviceSettings).toHaveBeenCalledWith('pocketbook')
    expect(store.deviceSettings.pocketbook).toBeNull()
    expect(store.deleting).toBe(false)
  })

  it('isConfigured returns true when settings exist, false otherwise', async () => {
    vi.mocked(SettingsService.getAllSettings).mockReturnValue(resolving(mockAllSettings))

    const store = useSettingsStore()
    await store.fetchSettings()

    expect(store.isConfigured('kindle')).toBe(true)
    expect(store.isConfigured('pocketbook')).toBe(false)
  })
})
