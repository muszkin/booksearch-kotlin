import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { LibraryService, SettingsService, DeliverService, TranslationService, TranslationJobState } from '@/api/generated'
import type { LibraryBook } from '@/api/generated'
import LibraryView from '../LibraryView.vue'
import { useTranslationStore } from '@/stores/translation'

vi.mock('@/components/library/LibraryCoverImage.vue', () => ({ default: { template: '<div />' } }))
const book = { id: 1, bookMd5: 'md5', format: 'epub', filePath: '/book.epub', title: 'English EPUB', author: 'Author', addedAt: '2026-09-12' } as LibraryBook
let wrapper: ReturnType<typeof mount> | undefined
beforeEach(() => {
  setActivePinia(createPinia())
  vi.spyOn(LibraryService, 'getUserLibrary').mockResolvedValue({ items: [book], page: 1, pageSize: 20, totalItems: 1, totalPages: 1 })
  vi.spyOn(SettingsService, 'getAllSettings').mockResolvedValue({})
  vi.spyOn(DeliverService, 'getUserDeliveries').mockResolvedValue([])
  vi.spyOn(TranslationService, 'estimateTranslation').mockResolvedValue({ totalChapters: 2, estimatedInputTokens: 200, modelId: 'free/model', indicativeDuration: 'minutes_to_hours', limitWarning: 'Daily limits apply.' })
  vi.spyOn(TranslationService, 'startTranslation').mockResolvedValue({ jobId: 'job-id', status: TranslationJobState.QUEUED })
})
afterEach(() => {
  wrapper?.unmount()
  vi.restoreAllMocks()
})

describe('LibraryView translation integration', () => {
  it('opens estimate without starting, confirms, shows progress, and cleans polling on unmount', async () => {
    wrapper = mount(LibraryView)
    await flushPromises()
    await wrapper.get('[data-testid="translate-btn"]').trigger('click')
    await flushPromises()
    expect(TranslationService.estimateTranslation).toHaveBeenCalledExactlyOnceWith(1)
    expect(TranslationService.startTranslation).not.toHaveBeenCalled()
    expect(wrapper.get('[role="dialog"]').text()).toContain(book.title)
    await wrapper.get('[data-testid="external-processing-confirmation"]').setValue(true)
    await wrapper.get('[data-testid="translation-start-btn"]').trigger('click')
    await flushPromises()
    expect(TranslationService.startTranslation).toHaveBeenCalledExactlyOnceWith(1, { externalProcessingConfirmed: true })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.get('[aria-label="Translation progress"]').text()).toContain('0 / 2')
    expect(wrapper.get('[data-testid="translate-btn"]').attributes('disabled')).toBeDefined()
    const cleanup = vi.spyOn(useTranslationStore(), 'cleanup')
    wrapper.unmount()
    wrapper = undefined
    expect(cleanup).toHaveBeenCalledOnce()
  })

  it('keeps start disabled when the estimate fails and supports retry', async () => {
    vi.mocked(TranslationService.estimateTranslation).mockRejectedValueOnce(new Error('unavailable'))
    wrapper = mount(LibraryView)
    await flushPromises()
    await wrapper.get('[data-testid="translate-btn"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="dialog"] [role="alert"]').text()).toContain('Could not estimate')
    await wrapper.get('[data-testid="external-processing-confirmation"]').setValue(true)
    expect(wrapper.get('[data-testid="translation-start-btn"]').attributes('disabled')).toBeDefined()
    await wrapper.findAll('button').find((button) => button.text() === 'Retry estimate')!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="translation-start-btn"]').attributes('disabled')).toBeUndefined()
  })
})
