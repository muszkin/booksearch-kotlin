import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TranslationDialog from '../TranslationDialog.vue'

const estimate = {
  totalChapters: 12, estimatedInputTokens: 45000, modelId: 'provider/free',
  indicativeDuration: 'minutes_to_hours', limitWarning: 'Free endpoints have daily limits.',
}
const wrappers: ReturnType<typeof mount>[] = []
function render(props = {}) {
  const wrapper = mount(TranslationDialog, { props: { estimate, ...props }, attachTo: document.body })
  wrappers.push(wrapper)
  return wrapper
}
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))

describe('TranslationDialog', () => {
  it('disables start until explicit consent and emits start only after confirmation', async () => {
    const wrapper = render()
    const start = wrapper.get('[data-testid="translation-start-btn"]')
    expect(start.attributes('disabled')).toBeDefined()
    expect(wrapper.get<HTMLInputElement>('[data-testid="external-processing-confirmation"]').element.checked).toBe(false)
    await wrapper.get('[data-testid="external-processing-confirmation"]').setValue(true)
    expect(start.attributes('disabled')).toBeUndefined()
    await start.trigger('click')
    expect(wrapper.emitted('start')).toHaveLength(1)
  })

  it('shows the exact notice, estimate, selected model, duration and free limit warning', () => {
    const wrapper = render()
    expect(wrapper.text()).toContain('Treść EPUB-a oraz wybrany glosariusz i fragmenty kontekstu zostaną wysłane do OpenRouter w celu tłumaczenia')
    expect(wrapper.text()).toContain('W tej wersji nie wybrano materiałów kontekstowych.')
    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('45000')
    expect(wrapper.text()).toContain('provider/free')
    expect(wrapper.text()).toContain('minutes to hours')
    expect(wrapper.text()).toContain(estimate.limitWarning)
  })

  it.each([{ estimate: null, loading: true }, { estimate: null, error: 'Translation is not configured.' }, { starting: true }])(
    'prevents starting while loading, unavailable or already starting: %j', async (props) => {
      const wrapper = render(props)
      await wrapper.get('[data-testid="external-processing-confirmation"]').setValue(true)
      expect(wrapper.get('[data-testid="translation-start-btn"]').attributes('disabled')).toBeDefined()
      if ('error' in props) expect(wrapper.get('[role="alert"]').text()).toContain(props.error)
    },
  )

  it('focuses the heading, contains keyboard focus, closes with Escape and restores focus', async () => {
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()
    const wrapper = render()
    expect(document.activeElement).toBe(wrapper.get('h2').element)
    const last = wrapper.get('[data-testid="translation-close-btn"]')
    ;(last.element as HTMLElement).focus()
    await last.trigger('keydown', { key: 'Tab' })
    expect(document.activeElement).toBe(wrapper.get('[data-testid="external-processing-confirmation"]').element)
    await wrapper.get('[role="dialog"]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
    wrappers.pop()
    expect(document.activeElement).toBe(trigger)
    trigger.remove()
  })

  it.each([false, true])('retains focus when starting disables every control (shiftKey=%s)', async (shiftKey) => {
    const wrapper = render()
    await wrapper.get('[data-testid="external-processing-confirmation"]').setValue(true)
    ;(wrapper.get('[data-testid="translation-start-btn"]').element as HTMLElement).focus()
    await wrapper.setProps({ starting: true })
    const event = new KeyboardEvent('keydown', { key: 'Tab', shiftKey, bubbles: true, cancelable: true })
    wrapper.get('[role="dialog"]').element.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(wrapper.get('h2').element)
  })
})
