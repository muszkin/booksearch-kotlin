import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import FacetSelect from '../FacetSelect.vue'
import type { Facet } from '@/stores/search'

const items: Facet[] = [
  { value: 'Lem', count: 12 },
  { value: 'Dick', count: 3 },
  { value: 'Herbert', count: 2 },
]

function mountSelect(propsOverrides = {}) {
  return mount(FacetSelect, {
    props: {
      label: 'Author',
      items,
      hidden: new Set<string>(),
      searchable: true,
      ...propsOverrides,
    },
    attachTo: document.body,
  })
}

describe('FacetSelect', () => {
  it('keeps the list closed until the trigger is pressed', () => {
    const wrapper = mountSelect()

    expect(wrapper.find('[data-testid="facet-list"]').exists()).toBe(false)
  })

  it('opens the list on the trigger', async () => {
    const wrapper = mountSelect()

    await wrapper.get('[data-testid="facet-trigger"]').trigger('click')

    expect(wrapper.get('[data-testid="facet-list"]').text()).toContain('Lem')
  })

  it('mentions no hidden count while nothing is hidden', () => {
    const wrapper = mountSelect()

    const trigger = wrapper.get('[data-testid="facet-trigger"]').text()
    expect(trigger).toContain('Author')
    expect(trigger).not.toContain('hidden')
  })

  it('counts the excluded values on the trigger', () => {
    const wrapper = mountSelect({ hidden: new Set(['Dick', 'Herbert']) })

    expect(wrapper.get('[data-testid="facet-trigger"]').text()).toContain('2 hidden')
  })

  it('emits the value whose checkbox changed', async () => {
    const wrapper = mountSelect()
    await wrapper.get('[data-testid="facet-trigger"]').trigger('click')

    await wrapper.get('[data-testid="facet-option-Lem"]').setValue(false)

    expect(wrapper.emitted('toggle')).toEqual([['Lem']])
  })

  it('narrows the options to those matching the typed text', async () => {
    const wrapper = mountSelect()
    await wrapper.get('[data-testid="facet-trigger"]').trigger('click')

    await wrapper.get('[data-testid="facet-search"]').setValue('di')

    expect(wrapper.find('[data-testid="facet-option-Dick"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="facet-option-Lem"]').exists()).toBe(false)
  })

  it('offers no search box when the caller did not ask for one', async () => {
    const wrapper = mountSelect({ searchable: false })
    await wrapper.get('[data-testid="facet-trigger"]').trigger('click')

    expect(wrapper.find('[data-testid="facet-search"]').exists()).toBe(false)
  })

  it('closes the list on Escape', async () => {
    const wrapper = mountSelect()
    await wrapper.get('[data-testid="facet-trigger"]').trigger('click')

    await wrapper.get('[data-testid="facet-panel"]').trigger('keydown', { key: 'Escape' })

    expect(wrapper.find('[data-testid="facet-list"]').exists()).toBe(false)
  })

  it('says so when nothing matches the typed text', async () => {
    const wrapper = mountSelect()
    await wrapper.get('[data-testid="facet-trigger"]').trigger('click')

    await wrapper.get('[data-testid="facet-search"]').setValue('zzz')

    expect(wrapper.get('[data-testid="facet-list"]').text()).toContain('No matches')
  })

  it('selects every listed value at once', async () => {
    const wrapper = mountSelect({ hidden: new Set(['Lem', 'Dick']) })
    await wrapper.get('[data-testid="facet-trigger"]').trigger('click')

    await wrapper.get('[data-testid="facet-select-all"]').trigger('click')

    expect(wrapper.emitted('set-many')).toEqual([[{ values: ['Lem', 'Dick', 'Herbert'], hidden: false }]])
  })

  it('deselects every listed value at once', async () => {
    const wrapper = mountSelect()
    await wrapper.get('[data-testid="facet-trigger"]').trigger('click')

    await wrapper.get('[data-testid="facet-deselect-all"]').trigger('click')

    expect(wrapper.emitted('set-many')).toEqual([[{ values: ['Lem', 'Dick', 'Herbert'], hidden: true }]])
  })

  it('applies bulk actions only to what the search left listed', async () => {
    const wrapper = mountSelect()
    await wrapper.get('[data-testid="facet-trigger"]').trigger('click')
    await wrapper.get('[data-testid="facet-search"]').setValue('e')

    await wrapper.get('[data-testid="facet-deselect-all"]').trigger('click')

    const emitted = wrapper.emitted('set-many')![0][0] as { values: string[]; hidden: boolean }
    expect(emitted.values).toEqual(['Lem', 'Herbert'])
    expect(emitted.hidden).toBe(true)
  })

  it('offers no bulk actions when the search matched nothing', async () => {
    const wrapper = mountSelect()
    await wrapper.get('[data-testid="facet-trigger"]').trigger('click')

    await wrapper.get('[data-testid="facet-search"]').setValue('zzz')

    expect(wrapper.find('[data-testid="facet-select-all"]').exists()).toBe(false)
  })
})
