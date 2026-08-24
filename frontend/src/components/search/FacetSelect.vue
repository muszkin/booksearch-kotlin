<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import type { Facet } from '@/stores/search'

interface Props {
  label: string
  items: Facet[]
  hidden: Set<string>
  searchable?: boolean
}

const props = withDefaults(defineProps<Props>(), { searchable: false })

const emit = defineEmits<{ toggle: [value: string] }>()

const open = ref(false)
const query = ref('')
const root = ref<HTMLElement | null>(null)

const hiddenCount = computed(() => props.items.filter((item) => props.hidden.has(item.value)).length)

const shownItems = computed(() => {
  const needle = query.value.trim().toLowerCase()
  if (needle === '') return props.items
  return props.items.filter((item) => item.value.toLowerCase().includes(needle))
})

watch(
  () => props.items,
  () => {
    query.value = ''
  },
)

function toggleOpen() {
  open.value = !open.value
  if (!open.value) query.value = ''
}

function close() {
  open.value = false
  query.value = ''
}

function onDocumentPointerDown(event: PointerEvent) {
  if (!open.value) return
  if (root.value && !root.value.contains(event.target as Node)) close()
}

document.addEventListener('pointerdown', onDocumentPointerDown)
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocumentPointerDown))
</script>

<template>
  <div ref="root" class="relative" data-testid="facet-panel" @keydown.escape="close">
    <button
      type="button"
      data-testid="facet-trigger"
      class="flex items-center gap-1.5 bg-zinc-800 border border-zinc-600 rounded px-2 py-1 text-sm text-zinc-100 hover:border-zinc-500"
      :aria-expanded="open"
      aria-haspopup="listbox"
      @click="toggleOpen"
    >
      <span>{{ props.label }}</span>
      <span v-if="hiddenCount > 0" class="text-xs text-amber-400">{{ hiddenCount }} hidden</span>
      <span aria-hidden="true" class="text-zinc-500">▾</span>
    </button>

    <div
      v-if="open"
      data-testid="facet-list"
      class="absolute z-20 mt-1 w-72 max-w-[80vw] bg-zinc-800 border border-zinc-600 rounded shadow-lg p-2"
      role="listbox"
    >
      <input
        v-if="props.searchable"
        v-model="query"
        data-testid="facet-search"
        type="search"
        :placeholder="`Filter ${props.label.toLowerCase()}`"
        :aria-label="`Filter ${props.label.toLowerCase()}`"
        class="mb-2 w-full bg-zinc-900 border border-zinc-600 rounded px-2 py-1 text-sm text-zinc-100 placeholder:text-zinc-500"
      />

      <p v-if="shownItems.length === 0" class="text-sm text-zinc-500 px-1 py-2">No matches</p>

      <ul v-else class="max-h-64 overflow-y-auto flex flex-col gap-1">
        <li v-for="item in shownItems" :key="item.value">
          <label class="flex items-center gap-2 text-sm text-zinc-300 px-1 py-0.5 hover:bg-zinc-700 rounded">
            <input
              type="checkbox"
              :data-testid="`facet-option-${item.value}`"
              :checked="!props.hidden.has(item.value)"
              class="accent-zinc-400 shrink-0"
              @change="emit('toggle', item.value)"
            />
            <span class="truncate" :title="item.value">{{ item.value }}</span>
            <span class="ml-auto text-zinc-500 shrink-0">{{ item.count }}</span>
          </label>
        </li>
      </ul>
    </div>
  </div>
</template>
