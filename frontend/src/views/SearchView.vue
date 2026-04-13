<script setup lang="ts">
import PageHeader from '@/components/layout/PageHeader.vue'
import EmptyState from '@/components/base/EmptyState.vue'
</script>

<template>
  <PageHeader title="Search" />
  <div class="p-6">
    <EmptyState
      title="Search for books"
      description="Use the search bar to find your next great read."
    />
  </div>
</template>
