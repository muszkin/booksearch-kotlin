<script setup lang="ts">
import { computed } from 'vue'
import FacetSelect from './FacetSelect.vue'
import type { Facet, SortDirection } from '@/stores/search'

interface FacetGroups {
  authors: Facet[]
  publishers: Facet[]
  formats: Facet[]
  languages: Facet[]
}

interface Props {
  facets: FacetGroups
  hiddenAuthors: Set<string>
  hiddenPublishers: Set<string>
  hiddenFormats: Set<string>
  hiddenLanguages: Set<string>
  sortDirection: SortDirection
  visibleCount: number
  totalCount: number
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'toggle-author': [value: string]
  'toggle-publisher': [value: string]
  'toggle-format': [value: string]
  'toggle-language': [value: string]
  'update:sortDirection': [value: SortDirection]
  clear: []
}>()

const sortOptions = [
  { value: 'none', label: 'Relevance' },
  { value: 'asc', label: 'Year, oldest first' },
  { value: 'desc', label: 'Year, newest first' },
]

/**
 * A group holding a single value offers no choice. Author lists run to dozens of names,
 * so that dropdown is the only one worth a search box.
 */
const groups = computed(() =>
  [
    { key: 'author', label: 'Author', items: props.facets.authors, hidden: props.hiddenAuthors, searchable: true },
    { key: 'publisher', label: 'Publisher', items: props.facets.publishers, hidden: props.hiddenPublishers, searchable: false },
    { key: 'format', label: 'Format', items: props.facets.formats, hidden: props.hiddenFormats, searchable: false },
    { key: 'language', label: 'Language', items: props.facets.languages, hidden: props.hiddenLanguages, searchable: false },
  ].filter((group) => group.items.length > 1),
)

const singular = computed(() => props.totalCount === 1)
const isFiltered = computed(() => props.visibleCount !== props.totalCount)

const toggleEvents = {
  author: 'toggle-author',
  publisher: 'toggle-publisher',
  format: 'toggle-format',
  language: 'toggle-language',
} as const

function onToggle(key: keyof typeof toggleEvents, value: string) {
  emit(toggleEvents[key] as 'toggle-author', value)
}
</script>

<template>
  <section class="bg-zinc-900 border-b border-zinc-700 px-4 py-2" aria-label="Result filters">
    <div data-testid="filter-bar" class="flex flex-wrap items-center gap-3">
      <label class="flex items-center gap-2 text-sm text-zinc-300">
        <span>Sort</span>
        <select
          data-testid="sort-direction"
          class="bg-zinc-800 border border-zinc-600 rounded px-2 py-1 text-sm text-zinc-100"
          :value="props.sortDirection"
          @change="emit('update:sortDirection', ($event.target as HTMLSelectElement).value as SortDirection)"
        >
          <option v-for="option in sortOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>

      <FacetSelect
        v-for="group in groups"
        :key="group.key"
        :label="group.label"
        :items="group.items"
        :hidden="group.hidden"
        :searchable="group.searchable"
        @toggle="onToggle(group.key as keyof typeof toggleEvents, $event)"
      />

      <p class="ml-auto text-sm text-zinc-400" aria-live="polite">
        {{ props.visibleCount }} of {{ props.totalCount }} {{ singular ? 'result' : 'results' }}
      </p>

      <button
        v-if="isFiltered"
        type="button"
        data-testid="clear-filters"
        class="text-sm text-zinc-300 underline hover:text-zinc-100"
        @click="emit('clear')"
      >
        Show all
      </button>
    </div>
  </section>
</template>
