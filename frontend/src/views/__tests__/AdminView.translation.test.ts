import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { AdminService, TranslationService } from '@/api/generated'
import type { UserResponse } from '@/api/generated'
import { useAuthStore } from '@/stores/auth'
import AdminView from '../AdminView.vue'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.spyOn(AdminService, 'listUsers').mockResolvedValue([])
  vi.spyOn(AdminService, 'getTranslationConfig').mockResolvedValue({ defaultModelId: null, configured: false, eligible: null })
  vi.spyOn(TranslationService, 'listTranslationModels').mockResolvedValue([])
})
afterEach(() => vi.restoreAllMocks())

describe('admin translation visibility', () => {
  it.each([
    { isSuperAdmin: true, actAsUserId: null, visible: true },
    { isSuperAdmin: false, actAsUserId: null, visible: false },
    { isSuperAdmin: true, actAsUserId: 8, visible: false },
  ])('shows settings only for a non-impersonating super-admin: %j', async ({ isSuperAdmin, actAsUserId, visible }) => {
    useAuthStore().user = { id: 7, isSuperAdmin, actAsUserId } as UserResponse
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: AdminView }] })
    const wrapper = mount(AdminView, { global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.find('#translation-model-heading').exists()).toBe(visible)
    expect(AdminService.getTranslationConfig).toHaveBeenCalledTimes(visible ? 1 : 0)
    wrapper.unmount()
  })
})
