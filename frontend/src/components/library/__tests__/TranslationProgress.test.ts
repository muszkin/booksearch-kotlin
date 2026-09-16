import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { TranslationJobState, TranslationService } from '@/api/generated'
import type { TranslationStatusResponse } from '@/api/generated'
import TranslationProgress from '../TranslationProgress.vue'

const job: TranslationStatusResponse = {
  jobId: 'job-uuid', sourceLibraryEntryId: 1, status: TranslationJobState.RUNNING,
  modelId: 'provider/free', totalChapters: 12, completedChapters: 4,
  estimatedInputTokens: 45000, actualInputTokens: 100, actualOutputTokens: 110, resumable: false,
}

describe('TranslationProgress', () => {
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

  it('downloads an available chapter and exposes actual model diagnostics', async () => {
    vi.spyOn(TranslationService, 'getTranslationDetails').mockResolvedValue({ options: {}, chapters: [
      { index: 0, href: 'chapter.xhtml', status: 'completed', attempts: 2, translatedSegments: 1, totalSegments: 1 },
      { index: 1, href: 'next.xhtml', status: 'pending', attempts: 0, translatedSegments: 0, totalSegments: 1 },
    ], attempts: [{ id: 'attempt', startedAt: '2026-09-16', chapterIndex: 0, segmentIndex: 0, requestedModel: 'openrouter/free', actualModel: 'resolved-free', status: 'failed', errorCode: 'item_count_mismatch', message: 'Expected 3 text items, received 2.', inputTokens: 10, outputTokens: 20, itemCount: 3 }] })
    vi.spyOn(TranslationService, 'exportTranslationChapter').mockResolvedValue('Cześć świecie!')
    const blobs: Blob[] = []
    vi.stubGlobal('URL', { createObjectURL: (blob: Blob) => { blobs.push(blob); return 'blob:test' }, revokeObjectURL: vi.fn() })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const wrapper = mount(TranslationProgress, { props: { job } })
    const details = wrapper.get('details')
    details.element.open = true
    await details.trigger('toggle')
    await flushPromises()
    expect(wrapper.text()).toContain('resolved-free')
    expect(wrapper.text()).toContain('Expected 3 text items, received 2.')
    const downloads = wrapper.findAll('button').filter(b => b.text() === 'Pobierz Markdown')
    expect(downloads).toHaveLength(1)
    await downloads[0]!.trigger('click')
    await flushPromises()
    expect(TranslationService.exportTranslationChapter).toHaveBeenCalledWith('job-uuid', 0, 'md')
    expect(blobs).toHaveLength(1)
    expect(click).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
  it('announces progress politely and exposes job details', () => {
    const wrapper = mount(TranslationProgress, { props: { job } })
    expect(wrapper.get('[aria-live="polite"]').text()).toContain('4 / 12')
    expect(wrapper.get('progress').attributes('value')).toBe('4')
    expect(wrapper.get('summary').text()).toContain('details')
    expect(wrapper.text()).toContain('provider/free')
    expect(wrapper.find('[data-testid="translation-cancel-btn"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="translation-resume-btn"]').exists()).toBe(false)
  })

  it('shows safe failure reason, resumes paused jobs and allows cancellation', async () => {
    const wrapper = mount(TranslationProgress, { props: { job: { ...job, status: TranslationJobState.PAUSED, resumable: true, error: 'model_no_longer_free' } } })
    expect(wrapper.text()).toContain('Wybrany model nie jest obecnie darmowy')
    await wrapper.get('[data-testid="translation-resume-btn"]').trigger('click')
    await wrapper.get('[data-testid="translation-cancel-btn"]').trigger('click')
    expect(wrapper.emitted('resume')).toHaveLength(1)
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('offers resume only when the server marks a failure resumable, and disables actions while pending', async () => {
    const wrapper = mount(TranslationProgress, { props: { job: { ...job, status: TranslationJobState.FAILED }, busy: true, error: 'Resume rejected: model is no longer free.' } })
    expect(wrapper.find('[data-testid="translation-resume-btn"]').exists()).toBe(false)
    expect(wrapper.get('[role="alert"]').text()).toContain('Resume rejected')
    await wrapper.setProps({ job: { ...job, status: TranslationJobState.FAILED, resumable: true } })
    expect(wrapper.get('[data-testid="translation-resume-btn"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="translation-cancel-btn"]').exists()).toBe(false)
  })

  it('identifies the separate library result after completion', () => {
    const wrapper = mount(TranslationProgress, { props: { job: { ...job, status: TranslationJobState.COMPLETED, outputLibraryEntryId: 42 } } })
    expect(wrapper.text()).toContain('42')
    expect(wrapper.text()).toContain('library')
  })
})
