<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { AdminService, TranslationService } from '@/api/generated'
import type { TranslationConfigResponse, TranslationModel } from '@/api/generated'
import BaseButton from '@/components/base/BaseButton.vue'
import AlertMessage from '@/components/base/AlertMessage.vue'

const config = ref<TranslationConfigResponse | null>(null)
const models = ref<TranslationModel[]>([])
const selectedModel = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const success = ref(false)
const canSave = computed(() => !loading.value && !saving.value && models.value.some((model) => model.id === selectedModel.value))
const eligibility = computed(() => config.value?.eligible === true ? 'Currently free' : config.value?.eligible === false ? 'Configured model is no longer free or available' : 'Free eligibility could not be checked')

async function refresh() {
  if (loading.value || saving.value) return
  loading.value = true
  error.value = null
  success.value = false
  // Failed live refresh must invalidate selectable models, not saved configuration.
  models.value = []
  const [configResult, modelsResult] = await Promise.allSettled([
    AdminService.getTranslationConfig(), TranslationService.listTranslationModels(),
  ])
  if (configResult.status === 'fulfilled') config.value = configResult.value
  if (modelsResult.status === 'fulfilled') models.value = modelsResult.value
  if (configResult.status === 'rejected' || modelsResult.status === 'rejected') {
    error.value = 'Could not refresh translation settings. Saved configuration is retained. Try again.'
  }
  selectedModel.value = models.value.some((model) => model.id === config.value?.defaultModelId) ? config.value!.defaultModelId! : ''
  loading.value = false
}

async function save() {
  if (!canSave.value) return
  saving.value = true
  error.value = null
  success.value = false
  try {
    config.value = await AdminService.updateTranslationConfig({ defaultModelId: selectedModel.value })
    success.value = true
  } catch {
    error.value = 'Could not save the model. Refresh the list and choose a currently free model.'
    models.value = []
    selectedModel.value = ''
  } finally {
    saving.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <section class="max-w-xl space-y-4" aria-labelledby="translation-model-heading" :aria-busy="loading || saving">
    <h2 id="translation-model-heading" class="text-base font-semibold text-zinc-200">Translation model</h2>
    <div v-if="config" class="space-y-1 text-sm text-zinc-300" aria-live="polite">
      <p>Saved model: <span class="break-all">{{ config.defaultModelId || 'None' }}</span></p>
      <p v-if="!config.configured">Translation is not configured. Server credentials and a default model are required.</p>
      <p>{{ eligibility }}</p>
    </div>
    <p class="text-sm text-zinc-400">Choose a currently free text model. Eligibility is checked again when saving, starting, or resuming a translation.</p>
    <AlertMessage v-if="error" variant="error" :message="error" />
    <p v-if="success" role="status" class="text-sm text-emerald-300">Translation model saved.</p>
    <label for="translation-model" class="block text-sm text-zinc-200">Free model</label>
    <select
      id="translation-model" v-model="selectedModel" :disabled="loading || saving || models.length === 0"
      class="min-h-[44px] w-full rounded-lg border border-zinc-600 bg-zinc-800 px-3 py-2 text-zinc-100 focus-visible:outline-2 focus-visible:outline-violet-400"
    >
      <option disabled value="">{{ loading ? 'Loading free models…' : 'Select a free model' }}</option>
      <option v-for="model in models" :key="model.id" :value="model.id">{{ model.name }} ({{ model.id }})</option>
    </select>
    <p v-if="!loading && !models.length && !error" role="status" class="text-sm text-amber-200">No free text models are currently available.</p>
    <div class="flex flex-wrap gap-3">
      <BaseButton data-testid="translation-model-save-btn" :disabled="!canSave" :loading="saving" @click="save">Save model</BaseButton>
      <BaseButton data-testid="translation-model-refresh-btn" variant="secondary" :disabled="loading || saving" @click="refresh">Refresh models</BaseButton>
    </div>
  </section>
</template>
