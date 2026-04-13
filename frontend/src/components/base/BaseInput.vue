<script setup lang="ts">
import { ref, computed, useId } from 'vue'

interface Props {
  label: string
  modelValue: string
  type?: string
  error?: string
  placeholder?: string
  autocomplete?: string
}

const props = withDefaults(defineProps<Props>(), {
  type: 'text',
  error: '',
  placeholder: '',
  autocomplete: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const inputId = useId()
const passwordVisible = ref(false)
const isPasswordField = computed(() => props.type === 'password')
const resolvedType = computed(() => {
  if (!isPasswordField.value) return props.type
  return passwordVisible.value ? 'text' : 'password'
})

function togglePasswordVisibility() {
  passwordVisible.value = !passwordVisible.value
}

function onInput(event: Event) {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', target.value)
}
</script>

<template>
  <div class="flex flex-col gap-1.5">
    <label
      :for="inputId"
      class="text-sm font-medium text-zinc-300"
    >
      {{ props.label }}
    </label>
    <div class="relative">
      <input
        :id="inputId"
        :type="resolvedType"
        :value="props.modelValue"
        :placeholder="props.placeholder"
        :autocomplete="props.autocomplete"
        :class="[
          'w-full rounded-lg bg-zinc-800 px-3 py-2 text-sm text-zinc-100',
          'min-h-[44px]',
          'border transition-colors duration-150',
          'placeholder:text-zinc-500',
          'focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-offset-zinc-900',
          props.error
            ? 'border-rose-400 focus:ring-rose-400'
            : 'border-zinc-600 focus:border-accent focus:ring-accent',
        ]"
        @input="onInput"
      />
      <button
        v-if="isPasswordField"
        type="button"
        data-testid="password-toggle"
        class="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-zinc-400 hover:text-zinc-200"
        :aria-label="passwordVisible ? 'Hide password' : 'Show password'"
        @click="togglePasswordVisibility"
      >
        <svg
          v-if="!passwordVisible"
          xmlns="http://www.w3.org/2000/svg"
          class="h-5 w-5"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          aria-hidden="true"
        >
          <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
          <circle cx="12" cy="12" r="3" />
        </svg>
        <svg
          v-else
          xmlns="http://www.w3.org/2000/svg"
          class="h-5 w-5"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          aria-hidden="true"
        >
          <path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24" />
          <line x1="1" y1="1" x2="23" y2="23" />
        </svg>
      </button>
    </div>
    <p
      v-if="props.error"
      class="text-xs text-rose-400"
      role="alert"
    >
      {{ props.error }}
    </p>
  </div>
</template>
