import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BookCard from '../BookCard.vue'
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

describe('BookCard UX refactor', () => {
  it('renders single Download button without separate Library button', () => {
    const wrapper = mount(BookCard, {
      props: {
        book: createBookResult(),
        selected: false,
      },
    })

    const downloadBtn = wrapper.find('[data-testid="download-btn"]')
    expect(downloadBtn.exists()).toBe(true)

    const libraryBtn = wrapper.find('[data-testid="add-library-btn"]')
    expect(libraryBtn.exists()).toBe(false)
  })

  it('shows Kindle and PocketBook buttons only when configured', () => {
    const propsDisabled = {
      book: createBookResult(),
      selected: false,
      kindleEnabled: false,
      pocketbookEnabled: false,
    }

    const wrapperDisabled = mount(BookCard, { props: propsDisabled })
    expect(wrapperDisabled.find('[data-testid="send-kindle-btn"]').exists()).toBe(false)
    expect(wrapperDisabled.find('[data-testid="send-pocketbook-btn"]').exists()).toBe(false)

    const propsEnabled = {
      book: createBookResult(),
      selected: false,
      kindleEnabled: true,
      pocketbookEnabled: true,
    }

    const wrapperEnabled = mount(BookCard, { props: propsEnabled })
    expect(wrapperEnabled.find('[data-testid="send-kindle-btn"]').exists()).toBe(true)
    expect(wrapperEnabled.find('[data-testid="send-pocketbook-btn"]').exists()).toBe(true)
  })

  it('emits deliver with device type when Kindle button clicked', async () => {
    const wrapper = mount(BookCard, {
      props: {
        book: createBookResult(),
        selected: false,
        kindleEnabled: true,
        pocketbookEnabled: false,
      },
    })

    await wrapper.find('[data-testid="send-kindle-btn"]').trigger('click')
    expect(wrapper.emitted('deliver')).toBeTruthy()
    expect(wrapper.emitted('deliver')![0]).toEqual(['kindle'])
  })
})
