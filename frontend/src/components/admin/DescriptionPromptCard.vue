<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import BaseButton from '@/components/base/BaseButton.vue'

interface Props {
  promptStyle: string
  minLength: number
  isDefault: boolean
  guard: string
  saving?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  save: [payload: { style: string; minLength: number }]
  reset: []
}>()

const draftStyle = ref(props.promptStyle)
const draftMinLength = ref(String(props.minLength))
const error = ref<string | null>(null)

watch(
  () => [props.promptStyle, props.minLength] as const,
  ([style, minLength]) => {
    draftStyle.value = style
    draftMinLength.value = String(minLength)
    error.value = null
  },
)

const parsedMinLength = computed(() => Number.parseInt(draftMinLength.value, 10))

function onSave() {
  if (draftStyle.value.trim() === '') {
    error.value = 'The description style cannot be empty.'
    return
  }
  if (Number.isNaN(parsedMinLength.value) || parsedMinLength.value < 0) {
    error.value = 'The minimum length must be zero or more.'
    return
  }
  error.value = null
  emit('save', { style: draftStyle.value, minLength: parsedMinLength.value })
}
</script>

<template>
  <section class="rounded-lg border border-zinc-700 bg-zinc-900 p-4">
    <h2 class="mb-1 text-base font-semibold text-zinc-200">AI description prompt</h2>
    <p class="mb-4 text-sm text-zinc-400">
      Applies to books with no publisher blurb. Changes take effect on the next description —
      no redeploy needed.
    </p>

    <label class="block text-sm text-zinc-300">
      <span class="mb-1 block">How books should be described</span>
      <textarea
        v-model="draftStyle"
        data-testid="prompt-style"
        rows="5"
        class="w-full rounded border border-zinc-600 bg-zinc-800 px-2 py-1 font-mono text-sm text-zinc-100"
      />
    </label>

    <label class="mt-3 block text-sm text-zinc-300">
      <span class="mb-1 block">Discard answers shorter than (characters)</span>
      <input
        v-model="draftMinLength"
        data-testid="prompt-min-length"
        type="number"
        min="0"
        class="w-32 rounded border border-zinc-600 bg-zinc-800 px-2 py-1 text-sm text-zinc-100"
      />
    </label>

    <div class="mt-4 rounded border border-zinc-700 bg-zinc-950 p-3">
      <p class="mb-1 text-xs uppercase tracking-wide text-zinc-500">
        Always appended — not editable
      </p>
      <p data-testid="prompt-guard" class="whitespace-pre-line font-mono text-xs text-zinc-400">
        {{ props.guard }}
      </p>
      <p class="mt-2 text-xs text-zinc-500">
        This rule keeps the model from inventing a plot for a book it does not know. It is
        always added to whatever you write above, so an edit here cannot remove it.
      </p>
    </div>

    <p v-if="error" data-testid="prompt-error" class="mt-3 text-sm text-red-400">{{ error }}</p>

    <div class="mt-4 flex items-center gap-3">
      <BaseButton
        data-testid="prompt-save"
        variant="primary"
        :disabled="props.saving === true"
        @click="onSave"
      >
        {{ props.saving ? 'Saving…' : 'Save' }}
      </BaseButton>

      <button
        v-if="!props.isDefault"
        type="button"
        data-testid="prompt-reset"
        class="text-sm text-zinc-300 underline hover:text-zinc-100"
        @click="emit('reset')"
      >
        Restore default
      </button>
    </div>
  </section>
</template>
