import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { TranslationJobState } from '@/api/generated'
import type { TranslationStatusResponse } from '@/api/generated'
import TranslationProgress from '../TranslationProgress.vue'

const job: TranslationStatusResponse = {
  jobId: 'job-uuid', sourceLibraryEntryId: 1, status: TranslationJobState.RUNNING,
  modelId: 'provider/free', totalChapters: 12, completedChapters: 4,
  estimatedInputTokens: 45000, actualInputTokens: 100, actualOutputTokens: 110, resumable: false,
}

describe('TranslationProgress', () => {
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
    expect(wrapper.text()).toContain('model_no_longer_free')
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
