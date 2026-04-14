<script setup lang="ts">
defineProps<{
  currentPage: number
  totalPages: number
}>()

const emit = defineEmits<{
  'page-change': [page: number]
}>()
</script>

<template>
  <nav
    class="flex items-center justify-center gap-3 py-4"
    aria-label="Pagination"
  >
    <button
      :disabled="currentPage <= 1"
      aria-label="Previous page"
      class="min-h-[44px] min-w-[44px] rounded-lg bg-zinc-800 px-4 py-2 text-sm font-medium text-zinc-300 transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-40"
      @click="emit('page-change', currentPage - 1)"
    >
      Previous
    </button>

    <span class="text-sm text-zinc-400">
      Page <span class="font-medium text-violet-400">{{ currentPage }}</span>
      of <span class="font-medium text-zinc-200">{{ totalPages }}</span>
    </span>

    <button
      :disabled="currentPage >= totalPages"
      aria-label="Next page"
      class="min-h-[44px] min-w-[44px] rounded-lg bg-zinc-800 px-4 py-2 text-sm font-medium text-zinc-300 transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-40"
      @click="emit('page-change', currentPage + 1)"
    >
      Next
    </button>
  </nav>
</template>
