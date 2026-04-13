<script setup lang="ts">
interface Props {
  title: string
  description?: string
}

const props = withDefaults(defineProps<Props>(), {
  description: '',
})
</script>

<template>
  <div class="flex flex-col items-center justify-center py-12 text-center">
    <div v-if="$slots.icon" class="mb-4 text-zinc-500">
      <slot name="icon" />
    </div>
    <h3 class="text-lg font-medium text-zinc-200">
      {{ props.title }}
    </h3>
    <p v-if="props.description" class="mt-1 max-w-sm text-sm text-zinc-400">
      {{ props.description }}
    </p>
    <div v-if="$slots.action" class="mt-6">
      <slot name="action" />
    </div>
  </div>
</template>
