import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DescriptionPromptCard from './DescriptionPromptCard.vue'

const GUARD = 'Reply with exactly the word UNKNOWN if you are not confident you know this book.'

function mountCard(propsOverrides = {}) {
  return mount(DescriptionPromptCard, {
    props: {
      promptStyle: 'You describe books for a library catalogue.',
      minLength: 80,
      isDefault: true,
      guard: GUARD,
      saving: false,
      ...propsOverrides,
    },
  })
}

describe('DescriptionPromptCard', () => {
  it('shows the editable style in a textarea', () => {
    const wrapper = mountCard()

    const textarea = wrapper.get('[data-testid="prompt-style"]')
    expect((textarea.element as HTMLTextAreaElement).value).toContain('library catalogue')
  })

  it('shows the guard as text the admin cannot edit', () => {
    const wrapper = mountCard()

    expect(wrapper.get('[data-testid="prompt-guard"]').text()).toContain('UNKNOWN')
    expect(wrapper.find('textarea[data-testid="prompt-guard"]').exists()).toBe(false)
  })

  it('explains that the guard is always appended', () => {
    const wrapper = mountCard()

    expect(wrapper.text()).toMatch(/always (appended|added)/i)
  })

  it('emits the edited style and length on save', async () => {
    const wrapper = mountCard()
    await wrapper.get('[data-testid="prompt-style"]').setValue('Write eight sentences.')
    await wrapper.get('[data-testid="prompt-min-length"]').setValue('20')

    await wrapper.get('[data-testid="prompt-save"]').trigger('click')

    expect(wrapper.emitted('save')).toEqual([[{ style: 'Write eight sentences.', minLength: 20 }]])
  })

  it('refuses to save an empty style', async () => {
    const wrapper = mountCard()
    await wrapper.get('[data-testid="prompt-style"]').setValue('   ')

    await wrapper.get('[data-testid="prompt-save"]').trigger('click')

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.text()).toMatch(/cannot be empty/i)
  })

  it('offers a reset only when the prompt has been customised', () => {
    expect(mountCard({ isDefault: true }).find('[data-testid="prompt-reset"]').exists()).toBe(false)
    expect(mountCard({ isDefault: false }).find('[data-testid="prompt-reset"]').exists()).toBe(true)
  })

  it('emits a reset request', async () => {
    const wrapper = mountCard({ isDefault: false })

    await wrapper.get('[data-testid="prompt-reset"]').trigger('click')

    expect(wrapper.emitted('reset')).toHaveLength(1)
  })

  it('disables saving while a save is in flight', () => {
    const wrapper = mountCard({ saving: true })

    expect(wrapper.get('[data-testid="prompt-save"]').attributes('disabled')).toBeDefined()
  })
})
