<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status: string
  progress: number
  error?: string
}>()

const STATUS_LABELS: Record<string, string> = {
  queued: 'Queued',
  downloading: 'Downloading',
  extracting_metadata: 'Extracting metadata',
  completed: 'Completed',
  failed: 'Failed',
}

const barColorClass = computed(() => {
  if (props.status === 'completed') return 'bg-emerald-400'
  if (props.status === 'failed') return 'bg-red-400'
  return 'bg-violet-400'
})

const showPercentage = computed(() =>
  props.status !== 'queued' && props.status !== 'failed',
)

const statusLabel = computed(() =>
  STATUS_LABELS[props.status] ?? props.status,
)
</script>

<template>
  <div class="w-full">
    <div
      role="progressbar"
      :aria-valuenow="progress"
      aria-valuemin="0"
      aria-valuemax="100"
      class="h-1.5 w-full overflow-hidden rounded-full bg-zinc-700"
    >
      <div
        :class="barColorClass"
        class="h-full rounded-full transition-all duration-500 ease-out"
        :style="{ width: `${status === 'failed' ? 100 : progress}%` }"
      />
    </div>

    <div class="mt-1 flex items-center justify-between text-xs">
      <span :class="status === 'failed' ? 'text-red-400' : 'text-zinc-400'">
        {{ statusLabel }}
      </span>

      <span v-if="showPercentage" class="text-zinc-400">
        {{ progress }}%
      </span>
    </div>

    <p v-if="error && status === 'failed'" class="mt-0.5 text-xs text-red-400">
      {{ error }}
    </p>
  </div>
</template>
