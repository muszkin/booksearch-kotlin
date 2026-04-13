import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { BookResult } from '@/api/generated'

export const useSelectionStore = defineStore('selection', () => {
  const selected = ref(new Map<string, BookResult>())

  const selectedBooks = computed(() => Array.from(selected.value.values()))
  const count = computed(() => selected.value.size)

  function isSelected(md5: string): boolean {
    return selected.value.has(md5)
  }

  function toggle(book: BookResult) {
    const next = new Map(selected.value)
    if (next.has(book.md5)) {
      next.delete(book.md5)
    } else {
      next.set(book.md5, book)
    }
    selected.value = next
  }

  function remove(md5: string) {
    const next = new Map(selected.value)
    next.delete(md5)
    selected.value = next
  }

  function clear() {
    selected.value = new Map()
  }

  return {
    selected,
    selectedBooks,
    count,
    isSelected,
    toggle,
    remove,
    clear,
  }
})
