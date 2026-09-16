import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { DownloadService, TranslationService } from '@/api/generated'
import type { LibraryBook, TranslationOptions } from '@/api/generated'
import TranslationOptionsForm from '../TranslationOptionsForm.vue'

afterEach(() => vi.restoreAllMocks())

describe('TranslationOptionsForm', () => {
  it('does not mark context ready while a selected reference preview is unavailable', async () => {
    vi.spyOn(TranslationService, 'listTranslationModels').mockResolvedValue([])
    vi.spyOn(TranslationService, 'listTranslationReferences').mockResolvedValue([])
    vi.spyOn(TranslationService, 'previewTranslationContext').mockRejectedValue(new Error('invalid EPUB'))
    const wrapper = mount(TranslationOptionsForm, { props: { sourceId: 1, modelValue: { referenceLibraryIds: [9] } } })
    await flushPromises()
    expect(wrapper.emitted('ready')).toEqual([[false]])
    expect(wrapper.text()).toContain('Nie udało się przygotować próbki')
    wrapper.unmount()
  })

  it('lets the reader select a free model and glossary and acquires references without delivery', async () => {
    const md5 = 'a'.repeat(32)
    const reference = { id: 9, bookMd5: md5, title: 'Polski przekład', author: 'Author' } as LibraryBook
    vi.spyOn(TranslationService, 'listTranslationModels').mockResolvedValue([{ id: 'free-model', name: 'Free' }])
    vi.spyOn(TranslationService, 'listTranslationReferences').mockResolvedValueOnce([]).mockResolvedValue([reference])
    vi.spyOn(TranslationService, 'downloadTranslationReference').mockResolvedValue({ jobId: 7, status: 'queued' })
    vi.spyOn(TranslationService, 'previewTranslationContext').mockResolvedValue({ referenceText: 'Rozdział 2: polskie nazewnictwo' })
    vi.spyOn(DownloadService, 'getDownloadStatus').mockResolvedValue({ jobId: 7, status: 'completed', progress: 100 })
    const wrapper = mount(TranslationOptionsForm, { props: { sourceId: 1, author: 'Author', modelValue: {},
      'onUpdate:modelValue': (options: TranslationOptions) => { void wrapper.setProps({ modelValue: options }) },
    } })
    await flushPromises()
    await wrapper.get('select').setValue('free-model')
    expect(wrapper.emitted('modelChange')?.at(-1)).toEqual(['free-model'])
    await wrapper.get('textarea[maxlength="12000"]').setValue('Conjoiners → Spójni')
    expect(wrapper.props('modelValue')).toMatchObject({ modelId: 'free-model', glossary: 'Conjoiners → Spójni' })
    await wrapper.get('input[placeholder^="Tytuł"]').setValue(`https://annas-archive.example/md5/${md5}`)
    await wrapper.findAll('button').find(b => b.text().startsWith('Wyszukaj'))!.trigger('click')
    await flushPromises()
    expect(TranslationService.downloadTranslationReference).toHaveBeenCalledWith(1, md5)
    expect(wrapper.props('modelValue').referenceLibraryIds).toEqual([9])
    expect(wrapper.text()).toContain('Referencja pobrana i wybrana')
    expect(wrapper.text()).toContain('Rozdział 2: polskie nazewnictwo')
    expect(wrapper.emitted('ready')?.at(-1)).toEqual([true])
    wrapper.unmount()
  })
})
