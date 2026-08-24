import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SearchFilters from '../SearchFilters.vue'
import type { Facet } from '@/stores/search'

const facets = {
  authors: [
    { value: 'Lem', count: 12 },
    { value: 'Dick', count: 3 },
  ] as Facet[],
  publishers: [{ value: 'WL', count: 9 }] as Facet[],
  formats: [
    { value: 'epub', count: 27 },
    { value: 'azw3', count: 2 },
  ] as Facet[],
  languages: [{ value: 'Polish [pl]', count: 40 }] as Facet[],
}

function mountFilters(propsOverrides = {}) {
  return mount(SearchFilters, {
    props: {
      facets,
      hiddenAuthors: new Set<string>(),
      hiddenPublishers: new Set<string>(),
      hiddenFormats: new Set<string>(),
      hiddenLanguages: new Set<string>(),
      sortDirection: 'none' as const,
      visibleCount: 30,
      totalCount: 30,
      ...propsOverrides,
    },
  })
}

describe('SearchFilters', () => {
  it('lists every facet value with its count', () => {
    const wrapper = mountFilters()

    const text = wrapper.text()
    expect(text).toContain('Lem')
    expect(text).toContain('12')
    expect(text).toContain('azw3')
  })

  it('shows a value as unchecked once it is hidden', () => {
    const wrapper = mountFilters({ hiddenAuthors: new Set(['Dick']) })

    const dick = wrapper.get('[data-testid="facet-author-Dick"]')
    expect((dick.element as HTMLInputElement).checked).toBe(false)
  })

  it('emits the toggled author when its checkbox changes', async () => {
    const wrapper = mountFilters()

    await wrapper.get('[data-testid="facet-author-Lem"]').setValue(false)

    expect(wrapper.emitted('toggle-author')).toEqual([['Lem']])
  })

  it('emits the toggled format when its checkbox changes', async () => {
    const wrapper = mountFilters()

    await wrapper.get('[data-testid="facet-format-epub"]').setValue(false)

    expect(wrapper.emitted('toggle-format')).toEqual([['epub']])
  })

  it('emits a sort change when a direction is picked', async () => {
    const wrapper = mountFilters()

    await wrapper.get('[data-testid="sort-direction"]').setValue('asc')

    expect(wrapper.emitted('update:sortDirection')).toEqual([['asc']])
  })

  it('reports how many results the filters leave visible', () => {
    const wrapper = mountFilters({ visibleCount: 12, totalCount: 50 })

    expect(wrapper.text()).toContain('12 of 50')
  })

  it('omits facet groups that have nothing to choose between', () => {
    const wrapper = mountFilters({
      facets: { ...facets, languages: [{ value: 'Polish [pl]', count: 40 }] },
    })

    expect(wrapper.find('[data-testid="facet-group-languages"]').exists()).toBe(false)
  })
})
