<script setup lang="ts">
type AlertVariant = 'success' | 'error' | 'warning' | 'info'

interface Props {
  variant: AlertVariant
  message?: string
}

const props = withDefaults(defineProps<Props>(), {
  message: '',
})

const variantClasses: Record<AlertVariant, string> = {
  success: 'text-emerald-300 border-emerald-500/30 bg-emerald-500/10',
  error: 'text-rose-300 border-rose-500/30 bg-rose-500/10',
  warning: 'text-amber-300 border-amber-500/30 bg-amber-500/10',
  info: 'text-sky-300 border-sky-500/30 bg-sky-500/10',
}

const iconPaths: Record<AlertVariant, string> = {
  success: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z',
  error: 'M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z',
  warning: 'M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z',
  info: 'M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
}
</script>

<template>
  <div
    role="alert"
    :class="[
      'flex items-start gap-3 rounded-lg border px-4 py-3 text-sm',
      variantClasses[props.variant],
    ]"
  >
    <svg
      xmlns="http://www.w3.org/2000/svg"
      class="mt-0.5 h-5 w-5 shrink-0"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      stroke-width="2"
      aria-hidden="true"
    >
      <path stroke-linecap="round" stroke-linejoin="round" :d="iconPaths[props.variant]" />
    </svg>
    <div>
      <slot>{{ props.message }}</slot>
    </div>
  </div>
</template>
