import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import LibraryBookCard from './LibraryBookCard.vue'

vi.mock('./LibraryCoverImage.vue', () => ({
  default: { name: 'LibraryCoverImage', template: '<div data-testid="cover-stub" />' },
}))
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
    descriptionSource: 'annas-archive',
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

  it('disables file actions and explains that download is still in progress', () => {
    const wrapper = mount(LibraryBookCard, {
      props: {
        ...defaultProps,
        book: createBook({ filePath: null }),
        downloadStatus: {
          jobId: 7,
          status: 'fetching_slow_download',
          progress: 40,
        },
        kindleEnabled: true,
        pocketbookEnabled: true,
      },
    })

    expect(wrapper.find('[data-testid="send-kindle-btn"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="send-pocketbook-btn"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="convert-mobi-btn"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="download-file-btn"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="start-download-btn"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="download-pending-note"]').text()).toContain(
      'still downloading',
    )
  })

  it('keeps a failed download visible and offers retry without enabling delivery', () => {
    const wrapper = mount(LibraryBookCard, {
      props: {
        ...defaultProps,
        book: createBook({ filePath: null }),
        downloadStatus: {
          jobId: 8,
          status: 'failed',
          progress: 40,
          error: 'Download challenge timed out',
        },
        kindleEnabled: false,
        pocketbookEnabled: true,
      },
    })

    expect(wrapper.text()).toContain('Download challenge timed out')
    expect(wrapper.find('[data-testid="start-download-btn"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="send-pocketbook-btn"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="download-pending-note"]').text()).toContain(
      'Download failed',
    )
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

  it('shows the stored description without asking the reader to expand anything', () => {
    const wrapper = mount(LibraryBookCard, {
      props: {
        ...defaultProps,
        book: createBook({ description: 'A sweeping tale of nothing much.' }),
      },
    })

    expect(wrapper.find('[data-testid="description-body"]').text()).toContain(
      'A sweeping tale of nothing much.',
    )
    expect(wrapper.find('[data-testid="description-generated-label"]').exists()).toBe(false)
  })

  it('labels a generated description so it is not mistaken for publisher copy', () => {
    const wrapper = mount(LibraryBookCard, {
      props: {
        ...defaultProps,
        book: createBook({ description: 'Guessed at.', descriptionSource: 'openrouter' }),
      },
    })

    expect(wrapper.find('[data-testid="description-generated-label"]').exists()).toBe(true)
  })

  it('offers regeneration only when a key is configured', () => {
    const withKey = mount(LibraryBookCard, {
      props: { ...defaultProps, canRegenerate: true },
    })
    const withoutKey = mount(LibraryBookCard, {
      props: { ...defaultProps, canRegenerate: false },
    })

    expect(withKey.find('[data-testid="description-regenerate"]').exists()).toBe(true)
    expect(withoutKey.find('[data-testid="description-regenerate"]').exists()).toBe(false)
  })

  it('emits regenerate-description when the reader asks for a fresh one', async () => {
    const wrapper = mount(LibraryBookCard, {
      props: { ...defaultProps, canRegenerate: true },
    })

    await wrapper.find('[data-testid="description-regenerate"]').trigger('click')

    expect(wrapper.emitted('regenerate-description')).toHaveLength(1)
  })

  it('reports the lookup while a description is still being resolved', () => {
    const wrapper = mount(LibraryBookCard, {
      props: {
        ...defaultProps,
        book: createBook({ description: '', descriptionSource: '' }),
        descriptionLoading: true,
      },
    })

    expect(wrapper.find('[data-testid="description-body"]').text()).toContain(
      'Looking for a description',
    )
  })

  it('says so when the lookup came back with nothing', () => {
    const wrapper = mount(LibraryBookCard, {
      props: {
        ...defaultProps,
        book: createBook({ description: '', descriptionSource: '' }),
        descriptionMissing: true,
      },
    })

    expect(wrapper.find('[data-testid="description-body"]').text()).toContain(
      'No description available',
    )
  })
})
