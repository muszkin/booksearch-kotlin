<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import apiClient from '@/api/client'

const props = defineProps<{
  libraryId: number
  fallbackUrl?: string
  alt: string
}>()

const src = ref<string | null>(null)
let blobUrl: string | null = null

async function load() {
  cleanup()
  try {
    const response = await apiClient.get(`/library/${props.libraryId}/cover`, {
      responseType: 'blob',
    })
    blobUrl = URL.createObjectURL(response.data)
    src.value = blobUrl
  } catch {
    src.value = props.fallbackUrl ?? null
  }
}

function cleanup() {
  if (blobUrl) {
    URL.revokeObjectURL(blobUrl)
    blobUrl = null
  }
}

onMounted(load)
onBeforeUnmount(cleanup)
watch(() => props.libraryId, load)
</script>

<template>
  <img
    v-if="src"
    :src="src"
    :alt="alt"
    class="w-20 h-30 rounded object-cover bg-zinc-700"
    loading="lazy"
  />
  <div
    v-else
    class="w-20 h-30 rounded bg-zinc-700"
    role="img"
    :aria-label="`No cover available for ${alt}`"
  />
</template>
