<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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

// Below this many authors the list is quicker to scan than to type into.
const AUTHOR_SEARCH_THRESHOLD = 8

const authorQuery = ref('')

const showAuthorSearch = computed(() => props.facets.authors.length > AUTHOR_SEARCH_THRESHOLD)

/**
 * Narrows which author checkboxes are listed. Hidden authors stay hidden while filtered
 * out of view — this only affects what the panel shows, never what the results contain.
 */
const shownAuthors = computed(() => {
  const needle = authorQuery.value.trim().toLowerCase()
  if (needle === '') return props.facets.authors
  return props.facets.authors.filter((author) => author.value.toLowerCase().includes(needle))
})

watch(
  () => props.facets.authors,
  () => {
    authorQuery.value = ''
  },
)

const sortOptions = [
  { value: 'none', label: 'Relevance' },
  { value: 'asc', label: 'Year, oldest first' },
  { value: 'desc', label: 'Year, newest first' },
]

/** A group with a single value offers no choice, so it only adds noise. */
const groups = computed(() =>
  [
    { key: 'authors', label: 'Author', items: shownAuthors.value, hidden: props.hiddenAuthors, event: 'toggle-author' },
    { key: 'publishers', label: 'Publisher', items: props.facets.publishers, hidden: props.hiddenPublishers, event: 'toggle-publisher' },
    { key: 'formats', label: 'Format', items: props.facets.formats, hidden: props.hiddenFormats, event: 'toggle-format' },
    { key: 'languages', label: 'Language', items: props.facets.languages, hidden: props.hiddenLanguages, event: 'toggle-language' },
  ].filter((group) => (group.key === 'authors' ? props.facets.authors.length > 1 : group.items.length > 1)),
)

const singular = computed(() => props.totalCount === 1)

function onToggle(event: string, value: string) {
  emit(event as 'toggle-author', value)
}
</script>

<template>
  <section class="bg-zinc-900 border-b border-zinc-700 px-4 py-3" aria-label="Result filters">
    <div class="flex flex-wrap items-center gap-4">
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

      <p class="text-sm text-zinc-400" aria-live="polite">
        {{ props.visibleCount }} of {{ props.totalCount }} {{ singular ? 'result' : 'results' }}
      </p>

      <button
        v-if="props.visibleCount !== props.totalCount"
        type="button"
        data-testid="clear-filters"
        class="text-sm text-zinc-300 underline hover:text-zinc-100"
        @click="emit('clear')"
      >
        Show all
      </button>
    </div>

    <div v-if="groups.length" class="mt-3 flex flex-wrap gap-6">
      <fieldset
        v-for="group in groups"
        :key="group.key"
        :data-testid="`facet-group-${group.key}`"
        class="min-w-40"
      >
        <legend class="text-xs uppercase tracking-wide text-zinc-500 mb-1">{{ group.label }}</legend>
        <input
          v-if="group.key === 'authors' && showAuthorSearch"
          v-model="authorQuery"
          data-testid="author-search"
          type="search"
          placeholder="Filter authors"
          aria-label="Filter authors"
          class="mb-1 w-full bg-zinc-800 border border-zinc-600 rounded px-2 py-1 text-sm text-zinc-100 placeholder:text-zinc-500"
        />
        <p
          v-if="group.key === 'authors' && group.items.length === 0"
          class="text-sm text-zinc-500"
        >
          No matching authors
        </p>
        <ul class="max-h-40 overflow-y-auto pr-2 flex flex-col gap-1">
          <li v-for="item in group.items" :key="item.value">
            <label class="flex items-center gap-2 text-sm text-zinc-300">
              <input
                type="checkbox"
                :data-testid="`facet-${group.key.slice(0, -1)}-${item.value}`"
                :checked="!group.hidden.has(item.value)"
                class="accent-zinc-400"
                @change="onToggle(group.event, item.value)"
              />
              <span class="truncate">{{ item.value }}</span>
              <span class="text-zinc-500">{{ item.count }}</span>
            </label>
          </li>
        </ul>
      </fieldset>
    </div>
  </section>
</template>
