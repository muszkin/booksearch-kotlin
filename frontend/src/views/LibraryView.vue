<script setup lang="ts">
import PageHeader from '@/components/layout/PageHeader.vue'
import EmptyState from '@/components/base/EmptyState.vue'
</script>

<template>
  <PageHeader title="Library" />
  <div class="p-6">
    <EmptyState
      title="Your library is empty"
      description="Books you download will appear here."
    >
      <template #action>
        <RouterLink
          :to="{ name: 'search' }"
          class="inline-flex items-center gap-2 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-zinc-900 transition-colors hover:bg-accent-hover"
        >
          Search for books
        </RouterLink>
      </template>
    </EmptyState>
  </div>
</template>
