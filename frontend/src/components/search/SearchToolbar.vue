<script setup lang="ts">
import BaseInput from '@/components/base/BaseInput.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseButton from '@/components/base/BaseButton.vue'

interface Props {
  query: string
  language: string
  format: string
  loading: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:query': [value: string]
  'update:language': [value: string]
  'update:format': [value: string]
  search: []
}>()

const languageOptions = [
  { value: 'pl', label: 'PL' },
  { value: 'en', label: 'EN' },
  { value: 'de', label: 'DE' },
]

const formatOptions = [
  { value: 'epub', label: 'EPUB' },
  { value: 'mobi', label: 'MOBI' },
  { value: 'pdf', label: 'PDF' },
]

function onSubmit() {
  emit('search')
}
</script>

<template>
  <form
    role="search"
    class="sticky z-10 bg-zinc-900 border-b border-zinc-700 p-4"
    @submit.prevent="onSubmit"
  >
    <div class="flex flex-wrap gap-3">
      <div class="w-full lg:flex-1">
        <BaseInput
          label="Search"
          :model-value="props.query"
          placeholder="Search for books..."
          @update:model-value="emit('update:query', $event)"
        />
      </div>
      <div class="flex gap-3 w-full lg:w-auto">
        <BaseSelect
          :model-value="props.language"
          :options="languageOptions"
          label="Language"
          class="w-28"
          @update:model-value="emit('update:language', $event)"
        />
        <BaseSelect
          :model-value="props.format"
          :options="formatOptions"
          label="Format"
          class="w-28"
          @update:model-value="emit('update:format', $event)"
        />
        <div class="flex items-end">
          <BaseButton
            type="submit"
            variant="primary"
            :loading="props.loading"
          >
            Search
          </BaseButton>
        </div>
      </div>
    </div>
  </form>
</template>
