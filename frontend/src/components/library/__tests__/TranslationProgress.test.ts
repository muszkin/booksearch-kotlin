import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { TranslationAttempt, TranslationJobState, TranslationService } from '@/api/generated'
import type { TranslationStatusResponse } from '@/api/generated'
import TranslationProgress from '../TranslationProgress.vue'

const job: TranslationStatusResponse = {
  jobId: 'job-uuid', sourceLibraryEntryId: 1, status: TranslationJobState.RUNNING,
  modelId: 'provider/free', totalChapters: 12, completedChapters: 4,
  estimatedInputTokens: 45000, actualInputTokens: 100, actualOutputTokens: 110, resumable: false,
}

describe('TranslationProgress', () => {
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals() })

  it.each([TranslationJobState.RUNNING, TranslationJobState.QUEUED])('explains automatic cooldown for %s', (status) => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-16T12:00:00Z'))
    const wrapper = mount(TranslationProgress, { props: { job: { ...job, status, retryAt: '2026-09-16T12:01:00Z', error: 'http_429' } } })
    expect(wrapper.text()).toContain('Limit OpenRouter. Następna próba nie wcześniej niż')
    expect(wrapper.get('time').attributes('datetime')).toBe('2026-09-16T12:01:00Z')
    expect(wrapper.get('time').text()).toContain('2026')
    expect(wrapper.text()).toContain('automatycznie')
    expect(wrapper.text()).not.toContain('Spróbuj innego darmowego modelu')
    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('gates manual Resume while retaining model editing and chapter export, then clears its timer', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-16T12:00:00Z'))
    vi.spyOn(TranslationService, 'getTranslationDetails').mockResolvedValue({ options: {}, chapters: [
      { index: 0, href: 'chapter.xhtml', status: 'completed', attempts: 1, translatedSegments: 1, totalSegments: 1 },
    ], attempts: [{ id: 'rate', startedAt: '2026-09-16T12:00:00Z', chapterIndex: 0, segmentIndex: 0, requestedModel: 'provider/free', status: 'failed', errorCode: 'http_429', inputTokens: 0, outputTokens: 0, itemCount: 1, retryAt: '2026-09-16T12:01:00Z', rateLimitScope: TranslationAttempt.rateLimitScope.PROVIDER, rateLimitLimit: 20, rateLimitRemaining: 0 }] })
    const wrapper = mount(TranslationProgress, { props: { job: { ...job, status: TranslationJobState.PAUSED, resumable: true, error: 'http_429', retryAt: '2026-09-16T12:01:00Z' } }, global: { stubs: { TranslationOptionsForm: true } } })
    expect(wrapper.text()).toContain('Resume będzie dostępne')
    const resume = wrapper.get('[data-testid="translation-resume-btn"]')
    expect(resume.attributes('disabled')).toBeDefined()
    await wrapper.findAll('button').find(b => b.text() === 'Zmień model / kontekst')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('translation-options-form-stub').exists()).toBe(true)
    expect(resume.attributes('disabled')).toBeDefined()
    await resume.trigger('click')
    expect(wrapper.emitted('resume')).toBeUndefined()
    expect(wrapper.findAll('button').find(b => b.text() === 'Pobierz TXT')!.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('Zakres limitu: dostawca')
    expect(wrapper.text()).toContain('Limit: 20')
    expect(wrapper.text()).toContain('Pozostało: 0')
    expect(wrapper.findAll('time')).toHaveLength(2)
    await vi.advanceTimersByTimeAsync(61_000)
    expect(resume.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('Możesz wznowić')
    await resume.trigger('click')
    expect(wrapper.emitted('resume')).toHaveLength(1)
    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('clears its live cooldown timer when the server clears the deadline', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-16T12:00:00Z'))
    const wrapper = mount(TranslationProgress, { props: { job: { ...job, retryAt: '2026-09-16T12:01:00Z' } } })
    expect(vi.getTimerCount()).toBe(1)
    await wrapper.setProps({ job: { ...job, retryAt: null } })
    expect(vi.getTimerCount()).toBe(0)
    wrapper.unmount()
  })

  it.each([
    ['request_timeout', 'nie odpowiedział w wyznaczonym czasie'],
    ['connection_failed', 'Nie udało się połączyć z OpenRouter'],
    ['invalid_provider_response', 'nieprawidłową strukturę'],
  ])('explains %s without exposing upstream payloads', (error, explanation) => {
    const wrapper = mount(TranslationProgress, { props: { job: { ...job, status: TranslationJobState.PAUSED, resumable: true, error } } })
    expect(wrapper.text()).toContain(explanation)
    expect(wrapper.find('[data-testid="translation-resume-btn"]').exists()).toBe(true)
    wrapper.unmount()
  })

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
