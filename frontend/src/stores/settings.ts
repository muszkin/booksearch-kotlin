import { ref } from 'vue'
import { defineStore } from 'pinia'
import { SettingsService } from '@/api/generated'
import type { DeviceSettingsResponse, DeviceSettingsRequest } from '@/api/generated'

type DeviceName = 'kindle' | 'pocketbook'

export const useSettingsStore = defineStore('settings', () => {
  const deviceSettings = ref<Record<DeviceName, DeviceSettingsResponse | null>>({
    kindle: null,
    pocketbook: null,
  })
  const loading = ref(false)
  const saving = ref(false)
  const deleting = ref(false)
  const error = ref<string | null>(null)
  const successMessage = ref<string | null>(null)

  function isConfigured(device: DeviceName): boolean {
    return deviceSettings.value[device] !== null
  }

  async function fetchSettings() {
    loading.value = true
    error.value = null

    try {
      const response = await SettingsService.getAllSettings()
      deviceSettings.value = {
        kindle: response['kindle'] ?? null,
        pocketbook: response['pocketbook'] ?? null,
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load settings'
    } finally {
      loading.value = false
    }
  }

  async function saveSettings(device: DeviceName, data: DeviceSettingsRequest) {
    saving.value = true
    error.value = null
    successMessage.value = null

    try {
      await SettingsService.saveDeviceSettings(device, data)
      await fetchSettings()
      successMessage.value = `${device.charAt(0).toUpperCase() + device.slice(1)} settings saved successfully`
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to save settings'
    } finally {
      saving.value = false
    }
  }

  async function deleteSettings(device: DeviceName) {
    deleting.value = true
    error.value = null
    successMessage.value = null

    try {
      await SettingsService.deleteDeviceSettings(device)
      deviceSettings.value[device] = null
      successMessage.value = `${device.charAt(0).toUpperCase() + device.slice(1)} settings deleted`
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to delete settings'
    } finally {
      deleting.value = false
    }
  }

  function clearMessages() {
    error.value = null
    successMessage.value = null
  }

  return {
    deviceSettings,
    loading,
    saving,
    deleting,
    error,
    successMessage,
    isConfigured,
    fetchSettings,
    saveSettings,
    deleteSettings,
    clearMessages,
  }
})
