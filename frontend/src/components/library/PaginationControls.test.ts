import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PaginationControls from './PaginationControls.vue'

describe('PaginationControls', () => {
  it('renders current page and total pages', () => {
    const wrapper = mount(PaginationControls, {
      props: { currentPage: 3, totalPages: 10 },
    })

    expect(wrapper.text()).toContain('3')
    expect(wrapper.text()).toContain('10')
  })

  it('previous button is disabled on page 1', () => {
    const wrapper = mount(PaginationControls, {
      props: { currentPage: 1, totalPages: 5 },
    })

    const prevButton = wrapper.find('[aria-label="Previous page"]')
    expect(prevButton.attributes('disabled')).toBeDefined()
  })

  it('next button is disabled on last page', () => {
    const wrapper = mount(PaginationControls, {
      props: { currentPage: 5, totalPages: 5 },
    })

    const nextButton = wrapper.find('[aria-label="Next page"]')
    expect(nextButton.attributes('disabled')).toBeDefined()
  })

  it('emits page-change with correct page number on click', async () => {
    const wrapper = mount(PaginationControls, {
      props: { currentPage: 3, totalPages: 10 },
    })

    const prevButton = wrapper.find('[aria-label="Previous page"]')
    await prevButton.trigger('click')
    expect(wrapper.emitted('page-change')?.[0]).toEqual([2])

    const nextButton = wrapper.find('[aria-label="Next page"]')
    await nextButton.trigger('click')
    expect(wrapper.emitted('page-change')?.[1]).toEqual([4])
  })
})
