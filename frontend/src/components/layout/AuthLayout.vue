<script setup lang="ts">
import AppLogo from './AppLogo.vue'
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-zinc-900 px-4">
    <div class="w-full max-w-md rounded-xl bg-zinc-800 p-8 shadow-lg">
      <div class="mb-6 flex justify-center">
        <AppLogo />
      </div>
      <slot />
    </div>
  </div>
</template>
