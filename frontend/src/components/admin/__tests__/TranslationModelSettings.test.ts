import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { AdminService, TranslationService } from '@/api/generated'
import TranslationModelSettings from '../TranslationModelSettings.vue'

beforeEach(() => {
  vi.spyOn(AdminService, 'getTranslationConfig').mockResolvedValue({ defaultModelId: 'saved/free', configured: true, eligible: false })
  vi.spyOn(TranslationService, 'listTranslationModels').mockResolvedValue([{ id: 'new/free', name: 'New free model' }])
  vi.spyOn(AdminService, 'updateTranslationConfig').mockResolvedValue({ defaultModelId: 'new/free', configured: true, eligible: true })
})
afterEach(() => vi.restoreAllMocks())

describe('TranslationModelSettings', () => {
  it('shows saved model eligibility and saves only a server-returned free choice', async () => {
    const wrapper = mount(TranslationModelSettings)
    await flushPromises()
    expect(wrapper.text()).toContain('saved/free')
    expect(wrapper.text()).toContain('no longer free or available')
    const save = wrapper.get('[data-testid="translation-model-save-btn"]')
    expect(save.attributes('disabled')).toBeDefined()
    await wrapper.get('select').setValue('new/free')
    expect(save.attributes('disabled')).toBeUndefined()
    await save.trigger('click')
    await flushPromises()
    expect(AdminService.updateTranslationConfig).toHaveBeenCalledExactlyOnceWith({ defaultModelId: 'new/free' })
    expect(wrapper.text()).toContain('Currently free')
  })

  it('rejects a DOM-injected model that was not returned by the server', async () => {
    const wrapper = mount(TranslationModelSettings)
    await flushPromises()
    const select = wrapper.get('select')
    const option = document.createElement('option')
    option.value = 'paid/model'
    select.element.appendChild(option)
    await select.setValue('paid/model')
    await wrapper.get('[data-testid="translation-model-save-btn"]').trigger('click')
    expect(AdminService.updateTranslationConfig).not.toHaveBeenCalled()
  })

  it('keeps saved configuration on list refresh failure and disables stale choices', async () => {
    const wrapper = mount(TranslationModelSettings)
    await flushPromises()
    await wrapper.get('select').setValue('new/free')
    vi.mocked(TranslationService.listTranslationModels).mockRejectedValueOnce(new Error('offline'))
    await wrapper.get('[data-testid="translation-model-refresh-btn"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('saved/free')
    expect(wrapper.get('[role="alert"]').text()).toContain('refresh')
    expect(wrapper.get('[data-testid="translation-model-save-btn"]').attributes('disabled')).toBeDefined()
    expect(AdminService.updateTranslationConfig).not.toHaveBeenCalled()
  })

  it('shows rejected save and retains the saved model', async () => {
    vi.mocked(AdminService.updateTranslationConfig).mockRejectedValueOnce(new Error('no longer free'))
    const wrapper = mount(TranslationModelSettings)
    await flushPromises()
    await wrapper.get('select').setValue('new/free')
    await wrapper.get('[data-testid="translation-model-save-btn"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('saved/free')
    expect(wrapper.get('[role="alert"]').text()).toContain('save')
  })
})
