import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BookCard from '../BookCard.vue'
import { BookResult } from '@/api/generated/models/BookResult'

function createBookResult(overrides: Partial<BookResult> = {}): BookResult {
  return {
    md5: 'abc123',
    title: 'Przestrzeń objawienia',
    author: 'Alastair Reynolds',
    language: 'pl',
    format: 'epub',
    fileSize: '2.4 MB',
    detailUrl: '/details/abc123',
    coverUrl: 'https://example.com/cover.jpg',
    publisher: 'Wydawnictwo MAG',
    year: '2005',
    description: 'A sci-fi novel',
    matchType: BookResult.matchType.NONE,
    ownedFormats: [],
    ...overrides,
  }
}

function mountCard(bookOverrides: Partial<BookResult> = {}, selected = false) {
  return mount(BookCard, {
    props: {
      book: createBookResult(bookOverrides),
      selected,
    },
  })
}

describe('BookCard', () => {
  it('renders title, author, publisher+year, format badge, file size, and language', () => {
    const wrapper = mountCard()

    expect(wrapper.text()).toContain('Przestrzeń objawienia')
    expect(wrapper.text()).toContain('Alastair Reynolds')
    expect(wrapper.text()).toContain('Wydawnictwo MAG')
    expect(wrapper.text()).toContain('2005')
    expect(wrapper.text()).toContain('epub')
    expect(wrapper.text()).toContain('2.4 MB')
    expect(wrapper.text()).toContain('pl')
  })

  it('renders correct ownership border color per matchType', () => {
    const exactWrapper = mountCard({ matchType: BookResult.matchType.EXACT })
    expect(exactWrapper.find('[data-testid="book-card"]').classes()).toContain('border-l-emerald-400')

    const titleWrapper = mountCard({ matchType: BookResult.matchType.TITLE })
    expect(titleWrapper.find('[data-testid="book-card"]').classes()).toContain('border-l-amber-400')

    const authorWrapper = mountCard({ matchType: BookResult.matchType.AUTHOR })
    expect(authorWrapper.find('[data-testid="book-card"]').classes()).toContain('border-l-sky-400')

    const noneWrapper = mountCard({ matchType: BookResult.matchType.NONE })
    const noneClasses = noneWrapper.find('[data-testid="book-card"]').classes()
    expect(noneClasses).not.toContain('border-l-emerald-400')
    expect(noneClasses).not.toContain('border-l-amber-400')
    expect(noneClasses).not.toContain('border-l-sky-400')
  })

  it('emits toggle-select on checkbox change and download on download click', async () => {
    const wrapper = mountCard()

    const checkbox = wrapper.find('input[type="checkbox"]')
    await checkbox.setValue(true)
    expect(wrapper.emitted('toggle-select')).toBeTruthy()

    const downloadBtn = wrapper.find('[data-testid="download-btn"]')
    await downloadBtn.trigger('click')
    expect(wrapper.emitted('download')).toBeTruthy()
  })
})
