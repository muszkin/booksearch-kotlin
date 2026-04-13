<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

interface Props {
  to: string
  label: string
  icon?: string
}

const props = defineProps<Props>()
const route = useRoute()

const isActive = computed(() => route.path === props.to)
</script>

<template>
  <li>
    <router-link
      :to="props.to"
      :aria-current="isActive ? 'page' : undefined"
      :class="[
        'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors duration-150',
        'min-h-[44px]',
        isActive
          ? 'bg-violet-400/10 text-violet-400'
          : 'text-zinc-400 hover:bg-zinc-700/50 hover:text-zinc-100',
      ]"
    >
      <slot name="icon" />
      <span>{{ props.label }}</span>
    </router-link>
  </li>
</template>
