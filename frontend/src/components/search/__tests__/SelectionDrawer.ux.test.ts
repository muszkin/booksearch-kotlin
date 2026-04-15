import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SelectionDrawer from '../SelectionDrawer.vue'
import { BookResult } from '@/api/generated/models/BookResult'

function createBookResult(overrides: Partial<BookResult> = {}): BookResult {
  return {
    md5: 'abc123',
    title: 'Przestrzen objawienia',
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

describe('SelectionDrawer UX refactor', () => {
  it('footer has Download All and conditional Kindle/PocketBook All buttons without Add All to Library', () => {
    const books = [createBookResult()]

    const wrapper = mount(SelectionDrawer, {
      props: {
        books,
        open: true,
        kindleEnabled: true,
        pocketbookEnabled: true,
      },
      global: {
        stubs: { Teleport: true },
      },
    })

    expect(wrapper.find('[data-testid="download-all-btn"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="add-all-library-btn"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="kindle-all-btn"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="pocketbook-all-btn"]').exists()).toBe(true)

    const wrapperNoDevices = mount(SelectionDrawer, {
      props: {
        books,
        open: true,
        kindleEnabled: false,
        pocketbookEnabled: false,
      },
      global: {
        stubs: { Teleport: true },
      },
    })

    expect(wrapperNoDevices.find('[data-testid="kindle-all-btn"]').exists()).toBe(false)
    expect(wrapperNoDevices.find('[data-testid="pocketbook-all-btn"]').exists()).toBe(false)
  })
})
