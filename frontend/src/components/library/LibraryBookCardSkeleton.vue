<script setup lang="ts">
</script>

<template>
  <div
    data-testid="skeleton-card"
    class="bg-zinc-800 border border-zinc-700 rounded-lg"
    aria-label="Loading book"
    role="status"
  >
    <div class="flex gap-4 p-4">
      <div class="animate-pulse bg-zinc-700 rounded w-20 h-30 shrink-0" />
      <div class="flex-1 space-y-3">
        <div class="animate-pulse bg-zinc-700 rounded h-4 w-3/4" />
        <div class="animate-pulse bg-zinc-700 rounded h-3 w-1/2" />
        <div class="animate-pulse bg-zinc-700 rounded h-3 w-1/3" />
        <div class="flex gap-2">
          <div class="animate-pulse bg-zinc-700 rounded h-5 w-12" />
          <div class="animate-pulse bg-zinc-700 rounded h-5 w-16" />
        </div>
      </div>
    </div>
    <div class="px-4 py-3 border-t border-zinc-700">
      <div class="animate-pulse bg-zinc-700 rounded h-8 w-full" />
    </div>
  </div>
</template>
