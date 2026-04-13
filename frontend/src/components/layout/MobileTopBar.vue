<script setup lang="ts">
import AppLogo from './AppLogo.vue'

defineEmits<{
  toggleMenu: []
}>()
</script>

<template>
  <div class="fixed inset-x-0 top-0 z-40 flex h-14 items-center gap-3 border-b border-zinc-700 bg-zinc-800 px-4 lg:hidden">
    <button
      type="button"
      class="flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg text-zinc-400 transition-colors hover:bg-zinc-700/50 hover:text-zinc-100"
      aria-label="Open menu"
      @click="$emit('toggleMenu')"
    >
      <svg
        class="h-6 w-6"
        xmlns="http://www.w3.org/2000/svg"
        fill="none"
        viewBox="0 0 24 24"
        stroke-width="1.5"
        stroke="currentColor"
        aria-hidden="true"
      >
        <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
      </svg>
    </button>
    <AppLogo />
  </div>
</template>
