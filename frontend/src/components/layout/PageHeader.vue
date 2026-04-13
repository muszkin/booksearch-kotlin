<script setup lang="ts">
interface Props {
  title: string
}

defineProps<Props>()
</script>

<template>
  <header
    class="sticky top-0 z-20 border-b border-zinc-700 bg-zinc-800/80 px-6 py-4 backdrop-blur"
  >
    <h1 class="text-lg font-semibold text-zinc-100">{{ title }}</h1>
  </header>
</template>
