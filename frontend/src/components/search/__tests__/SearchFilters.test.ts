import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SearchFilters from '../SearchFilters.vue'
import FacetSelect from '../FacetSelect.vue'
import type { Facet } from '@/stores/search'

const facets = {
  authors: [
    { value: 'Lem', count: 12 },
    { value: 'Dick', count: 3 },
  ] as Facet[],
  publishers: [
    { value: 'WL', count: 9 },
    { value: 'Mag', count: 2 },
  ] as Facet[],
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
    attachTo: document.body,
  })
}

describe('SearchFilters', () => {
  it('renders one dropdown per facet group worth choosing between', () => {
    const wrapper = mountFilters()

    // languages has a single value, so it offers no choice
    expect(wrapper.findAllComponents(FacetSelect)).toHaveLength(3)
  })

  it('labels each dropdown', () => {
    const wrapper = mountFilters()

    const labels = wrapper.findAllComponents(FacetSelect).map((c) => c.props('label'))
    expect(labels).toEqual(['Author', 'Publisher', 'Format'])
  })

  it('only offers a search box inside the author dropdown', () => {
    const wrapper = mountFilters()

    const searchable = wrapper.findAllComponents(FacetSelect).map((c) => c.props('searchable'))
    expect(searchable).toEqual([true, false, false])
  })

  it('forwards a toggled author', async () => {
    const wrapper = mountFilters()

    await wrapper.findAllComponents(FacetSelect)[0].vm.$emit('toggle', 'Lem')

    expect(wrapper.emitted('toggle-author')).toEqual([['Lem']])
  })

  it('forwards a toggled format', async () => {
    const wrapper = mountFilters()

    await wrapper.findAllComponents(FacetSelect)[2].vm.$emit('toggle', 'epub')

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

  it('offers a reset only while something is filtered out', () => {
    const unfiltered = mountFilters()
    expect(unfiltered.find('[data-testid="clear-filters"]').exists()).toBe(false)

    const filtered = mountFilters({ visibleCount: 12, totalCount: 50 })
    expect(filtered.find('[data-testid="clear-filters"]').exists()).toBe(true)
  })

  it('keeps the whole bar on one row', () => {
    const wrapper = mountFilters()

    expect(wrapper.get('[data-testid="filter-bar"]').classes()).toContain('flex')
  })

  it('forwards a bulk change for the group it came from', async () => {
    const wrapper = mountFilters()

    await wrapper
      .findAllComponents(FacetSelect)[1]
      .vm.$emit('set-many', { values: ['WL', 'Mag'], hidden: true })

    expect(wrapper.emitted('set-publishers')).toEqual([[{ values: ['WL', 'Mag'], hidden: true }]])
  })

  it('forwards a bulk restore for authors', async () => {
    const wrapper = mountFilters()

    await wrapper
      .findAllComponents(FacetSelect)[0]
      .vm.$emit('set-many', { values: ['Lem'], hidden: false })

    expect(wrapper.emitted('set-authors')).toEqual([[{ values: ['Lem'], hidden: false }]])
  })
})
