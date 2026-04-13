<script setup lang="ts">
import PageHeader from '@/components/layout/PageHeader.vue'
import EmptyState from '@/components/base/EmptyState.vue'
</script>

<template>
  <PageHeader title="Settings" />
  <div class="p-6">
    <EmptyState
      title="Settings"
      description="Configuration options will appear here."
    />
  </div>
</template>
