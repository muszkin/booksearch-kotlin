import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SelectionDrawer from '../SelectionDrawer.vue'
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

function mountDrawer(books: BookResult[] = [], open = true) {
  return mount(SelectionDrawer, {
    props: { books, open },
    global: {
      stubs: { Teleport: true },
    },
  })
}

describe('SelectionDrawer', () => {
  it('renders list of selected books with title, author, format, and remove button', () => {
    const books = [
      createBookResult({ md5: '1', title: 'Book A', author: 'Author A', format: 'epub' }),
      createBookResult({ md5: '2', title: 'Book B', author: 'Author B', format: 'pdf' }),
    ]
    const wrapper = mountDrawer(books)

    expect(wrapper.text()).toContain('Book A')
    expect(wrapper.text()).toContain('Author A')
    expect(wrapper.text()).toContain('epub')
    expect(wrapper.text()).toContain('Book B')
    expect(wrapper.text()).toContain('Author B')
    expect(wrapper.text()).toContain('pdf')

    const removeButtons = wrapper.findAll('[data-testid="remove-book-btn"]')
    expect(removeButtons.length).toBeGreaterThanOrEqual(2)
  })

  it('emits download-all when "Download All" button clicked', async () => {
    const books = [createBookResult()]
    const wrapper = mountDrawer(books)

    await wrapper.find('[data-testid="download-all-btn"]').trigger('click')
    expect(wrapper.emitted('download-all')).toBeTruthy()
  })

  it('emits add-all-to-library when "Add All to Library" button clicked', async () => {
    const books = [createBookResult()]
    const wrapper = mountDrawer(books)

    await wrapper.find('[data-testid="add-all-library-btn"]').trigger('click')
    expect(wrapper.emitted('add-all-to-library')).toBeTruthy()
  })

  it('emits clear when "Clear All" clicked and remove with md5 when individual remove clicked', async () => {
    const books = [
      createBookResult({ md5: 'book-1' }),
      createBookResult({ md5: 'book-2', title: 'Second Book' }),
    ]
    const wrapper = mountDrawer(books)

    await wrapper.find('[data-testid="clear-all-btn"]').trigger('click')
    expect(wrapper.emitted('clear')).toBeTruthy()

    const removeButtons = wrapper.findAll('[data-testid="remove-book-btn"]')
    await removeButtons[0].trigger('click')
    expect(wrapper.emitted('remove')).toBeTruthy()
    expect(wrapper.emitted('remove')![0]).toEqual(['book-1'])
  })
})
