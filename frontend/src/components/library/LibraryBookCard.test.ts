import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LibraryBookCard from './LibraryBookCard.vue'
import type { LibraryBook } from '@/api/generated/models/LibraryBook'
import type { DeliveryRecord } from '@/api/generated/models/DeliveryRecord'

function createBook(overrides: Partial<LibraryBook> = {}): LibraryBook {
  return {
    id: 1,
    bookMd5: 'abc123',
    format: 'epub',
    filePath: '/books/test.epub',
    addedAt: '2026-04-10T12:00:00Z',
    title: 'Test Book',
    author: 'Test Author',
    language: 'pl',
    fileSize: '2.5 MB',
    detailUrl: '/details/abc123',
    coverUrl: 'https://example.com/cover.jpg',
    publisher: 'Test Publisher',
    year: '2024',
    description: 'A test book',
    ...overrides,
  }
}

function createDelivery(overrides: Partial<DeliveryRecord> = {}): DeliveryRecord {
  return {
    id: 1,
    userId: 1,
    bookMd5: 'abc123',
    deviceType: 'kindle',
    status: 'sent',
    sentAt: '2026-04-10T13:00:00Z',
    createdAt: '2026-04-10T12:30:00Z',
    ...overrides,
  }
}

const defaultProps = {
  book: createBook(),
  deliveries: [] as DeliveryRecord[],
  kindleEnabled: false,
  pocketbookEnabled: false,
}

describe('LibraryBookCard', () => {
  it('renders book title, author, format badge, file size, and added date', () => {
    const wrapper = mount(LibraryBookCard, {
      props: defaultProps,
    })

    expect(wrapper.text()).toContain('Test Book')
    expect(wrapper.text()).toContain('Test Author')
    expect(wrapper.text()).toContain('epub')
    expect(wrapper.text()).toContain('2.5 MB')
    expect(wrapper.text()).toContain('2024')
  })

  it('shows download file button when filePath is present', () => {
    const wrapper = mount(LibraryBookCard, {
      props: defaultProps,
    })

    const downloadBtn = wrapper.find('[data-testid="download-file-btn"]')
    expect(downloadBtn.exists()).toBe(true)
  })

  it('hides Kindle/PocketBook buttons when device settings are not configured', () => {
    const wrapper = mount(LibraryBookCard, {
      props: {
        ...defaultProps,
        kindleEnabled: false,
        pocketbookEnabled: false,
      },
    })

    expect(wrapper.find('[data-testid="send-kindle-btn"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="send-pocketbook-btn"]').exists()).toBe(false)
  })

  it('shows Kindle/PocketBook buttons when device settings are configured', () => {
    const wrapper = mount(LibraryBookCard, {
      props: {
        ...defaultProps,
        kindleEnabled: true,
        pocketbookEnabled: true,
      },
    })

    expect(wrapper.find('[data-testid="send-kindle-btn"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="send-pocketbook-btn"]').exists()).toBe(true)
  })

  it('shows delivery indicators when deliveries exist', () => {
    const wrapper = mount(LibraryBookCard, {
      props: {
        ...defaultProps,
        deliveries: [
          createDelivery({ deviceType: 'kindle' }),
          createDelivery({ id: 2, deviceType: 'pocketbook' }),
        ],
      },
    })

    expect(wrapper.find('[data-testid="kindle-delivered"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="pocketbook-delivered"]').exists()).toBe(true)
  })

  it('shows convert buttons for formats excluding current format', () => {
    const wrapper = mount(LibraryBookCard, {
      props: {
        ...defaultProps,
        book: createBook({ format: 'epub' }),
      },
    })

    expect(wrapper.find('[data-testid="convert-epub-btn"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="convert-mobi-btn"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="convert-pdf-btn"]').exists()).toBe(true)
  })

  it('emits convert event with target format', async () => {
    const wrapper = mount(LibraryBookCard, {
      props: defaultProps,
    })

    await wrapper.find('[data-testid="convert-mobi-btn"]').trigger('click')
    expect(wrapper.emitted('convert')?.[0]).toEqual(['mobi'])
  })

  it('emits correct action events with payload', async () => {
    const wrapper = mount(LibraryBookCard, {
      props: {
        ...defaultProps,
        kindleEnabled: true,
        pocketbookEnabled: true,
      },
    })

    await wrapper.find('[data-testid="download-file-btn"]').trigger('click')
    expect(wrapper.emitted('download-file')).toHaveLength(1)

    await wrapper.find('[data-testid="send-kindle-btn"]').trigger('click')
    expect(wrapper.emitted('deliver')?.[0]).toEqual(['kindle'])

    await wrapper.find('[data-testid="send-pocketbook-btn"]').trigger('click')
    expect(wrapper.emitted('deliver')?.[1]).toEqual(['pocketbook'])

    await wrapper.find('[data-testid="remove-btn"]').trigger('click')
    expect(wrapper.emitted('remove')).toHaveLength(1)
  })
})
