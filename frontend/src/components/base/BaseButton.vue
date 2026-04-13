<script setup lang="ts">
type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost'

interface Props {
  variant?: ButtonVariant
  loading?: boolean
  disabled?: boolean
  type?: 'button' | 'submit' | 'reset'
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'primary',
  loading: false,
  disabled: false,
  type: 'button',
})

const variantClasses: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-zinc-900 hover:bg-accent-hover focus-visible:ring-accent',
  secondary: 'bg-surface text-zinc-100 hover:bg-surface-alt focus-visible:ring-zinc-500',
  danger: 'bg-rose-500 text-white hover:bg-rose-400 focus-visible:ring-rose-400',
  ghost: 'bg-transparent text-zinc-300 hover:bg-zinc-800 focus-visible:ring-zinc-500',
}
</script>

<template>
  <button
    :type="props.type"
    :disabled="props.disabled || props.loading"
    :class="[
      'inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium',
      'min-h-[44px] min-w-[44px]',
      'transition-colors duration-150',
      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-zinc-900',
      'disabled:cursor-not-allowed disabled:opacity-50',
      variantClasses[props.variant],
    ]"
  >
    <svg
      v-if="props.loading"
      data-testid="loading-spinner"
      class="h-4 w-4 animate-spin"
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      aria-hidden="true"
    >
      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
    </svg>
    <slot />
  </button>
</template>
