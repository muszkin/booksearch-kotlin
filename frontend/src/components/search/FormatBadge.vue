<script setup lang="ts">
interface Props {
  format: string
}

defineProps<Props>()
</script>

<template>
  <span class="text-xs px-2 py-0.5 rounded bg-zinc-700 text-zinc-300 uppercase">
    {{ format }}
  </span>
</template>
