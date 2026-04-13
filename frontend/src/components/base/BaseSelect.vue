<script setup lang="ts">
import { useId } from 'vue'

interface SelectOption {
  value: string
  label: string
}

interface Props {
  modelValue: string
  options: SelectOption[]
  label?: string
  id?: string
}

const props = withDefaults(defineProps<Props>(), {
  label: '',
  id: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const generatedId = useId()
const selectId = props.id || generatedId

function onChange(event: Event) {
  const target = event.target as HTMLSelectElement
  emit('update:modelValue', target.value)
}
</script>

<template>
  <div class="flex flex-col gap-1.5">
    <label
      v-if="props.label"
      :for="selectId"
      class="text-sm font-medium text-zinc-300"
    >
      {{ props.label }}
    </label>
    <select
      :id="selectId"
      :value="props.modelValue"
      :class="[
        'w-full rounded-lg bg-zinc-800 px-3 py-2 text-sm text-zinc-100',
        'min-h-[44px]',
        'border border-zinc-600 transition-colors duration-150',
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-zinc-900 focus-visible:ring-accent',
        'appearance-none cursor-pointer',
      ]"
      @change="onChange"
    >
      <option
        v-for="option in props.options"
        :key="option.value"
        :value="option.value"
      >
        {{ option.label }}
      </option>
    </select>
  </div>
</template>
