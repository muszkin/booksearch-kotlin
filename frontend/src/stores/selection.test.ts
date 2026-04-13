import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSelectionStore } from './selection'
import type { BookResult } from '@/api/generated'
import { BookResult as BookResultEnum } from '@/api/generated'

const createBook = (md5: string, title: string = 'Test Book'): BookResult => ({
  md5,
  title,
  author: 'Author',
  language: 'pl',
  format: 'epub',
  fileSize: '1 MB',
  detailUrl: `/detail/${md5}`,
  coverUrl: `/covers/${md5}.jpg`,
  publisher: 'Publisher',
  year: '2024',
  description: 'Description',
  matchType: BookResultEnum.matchType.NONE,
  ownedFormats: [],
})

describe('useSelectionStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('toggle adds book if not selected and removes if already selected', () => {
    const store = useSelectionStore()
    const book = createBook('abc123')

    store.toggle(book)
    expect(store.isSelected('abc123')).toBe(true)
    expect(store.count).toBe(1)

    store.toggle(book)
    expect(store.isSelected('abc123')).toBe(false)
    expect(store.count).toBe(0)
  })

  it('clear removes all selections, remove removes single item, count is correct', () => {
    const store = useSelectionStore()
    const book1 = createBook('abc')
    const book2 = createBook('def')
    const book3 = createBook('ghi')

    store.toggle(book1)
    store.toggle(book2)
    store.toggle(book3)
    expect(store.count).toBe(3)

    store.remove('def')
    expect(store.count).toBe(2)
    expect(store.isSelected('def')).toBe(false)
    expect(store.isSelected('abc')).toBe(true)

    expect(store.selectedBooks).toHaveLength(2)

    store.clear()
    expect(store.count).toBe(0)
    expect(store.selectedBooks).toEqual([])
  })
})
