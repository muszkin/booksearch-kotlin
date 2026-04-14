<script setup lang="ts">
import { ref, watch } from 'vue'
import { useSettingsStore } from '@/stores/settings'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import AlertMessage from '@/components/base/AlertMessage.vue'

type DeviceName = 'kindle' | 'pocketbook'

interface Props {
  device: DeviceName
  title: string
}

const props = defineProps<Props>()
const settingsStore = useSettingsStore()

const host = ref('')
const port = ref('')
const username = ref('')
const password = ref('')
const fromEmail = ref('')
const recipientEmail = ref('')
const localError = ref<string | null>(null)
const localSuccess = ref<string | null>(null)

function populateFields() {
  const settings = settingsStore.deviceSettings[props.device]
  if (settings) {
    host.value = settings.host
    port.value = settings.port
    username.value = settings.username
    password.value = ''
    fromEmail.value = settings.fromEmail
    recipientEmail.value = settings.recipientEmail
  } else {
    host.value = ''
    port.value = ''
    username.value = ''
    password.value = ''
    fromEmail.value = ''
    recipientEmail.value = ''
  }
}

watch(
  () => settingsStore.deviceSettings[props.device],
  () => populateFields(),
  { immediate: true },
)

function validateFields(): string | null {
  if (!host.value.trim()) return 'SMTP Host is required'
  if (!port.value.trim()) return 'SMTP Port is required'
  const portNum = Number(port.value)
  if (isNaN(portNum) || portNum < 1 || portNum > 65535) return 'Port must be a valid number (1-65535)'
  if (!username.value.trim()) return 'Username is required'
  if (!password.value && !settingsStore.isConfigured(props.device)) return 'Password is required'
  if (!fromEmail.value.trim()) return 'From Email is required'
  if (!recipientEmail.value.trim()) return 'Recipient Email is required'
  return null
}

async function handleSave() {
  localError.value = null
  localSuccess.value = null

  const validationError = validateFields()
  if (validationError) {
    localError.value = validationError
    return
  }

  await settingsStore.saveSettings(props.device, {
    host: host.value.trim(),
    port: Number(port.value),
    username: username.value.trim(),
    password: password.value,
    fromEmail: fromEmail.value.trim(),
    recipientEmail: recipientEmail.value.trim(),
  })

  if (settingsStore.error) {
    localError.value = settingsStore.error
  } else {
    localSuccess.value = settingsStore.successMessage
  }
}

async function handleDelete() {
  if (!confirm(`Delete ${props.title} SMTP configuration?`)) return

  localError.value = null
  localSuccess.value = null

  await settingsStore.deleteSettings(props.device)

  if (settingsStore.error) {
    localError.value = settingsStore.error
  } else {
    localSuccess.value = settingsStore.successMessage
  }
}
</script>

<template>
  <div class="rounded-lg border border-zinc-700 bg-zinc-800 p-6">
    <div class="mb-4 flex items-center justify-between">
      <h3 class="text-lg font-medium text-zinc-100">{{ props.title }}</h3>
      <span
        data-testid="status-badge"
        :class="[
          'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
          settingsStore.isConfigured(props.device)
            ? 'bg-emerald-400/10 text-emerald-400'
            : 'bg-zinc-500/10 text-zinc-500',
        ]"
      >
        {{ settingsStore.isConfigured(props.device) ? 'Configured' : 'Not configured' }}
      </span>
    </div>

    <AlertMessage
      v-if="localError"
      variant="error"
      :message="localError"
      class="mb-4"
    />

    <AlertMessage
      v-if="localSuccess"
      variant="success"
      :message="localSuccess"
      class="mb-4"
    />

    <form class="flex flex-col gap-4" @submit.prevent="handleSave">
      <div class="grid gap-4 sm:grid-cols-2">
        <BaseInput
          v-model="host"
          label="SMTP Host"
          placeholder="smtp.gmail.com"
        />
        <BaseInput
          v-model="port"
          label="SMTP Port"
          placeholder="587"
        />
      </div>

      <div class="grid gap-4 sm:grid-cols-2">
        <BaseInput
          v-model="username"
          label="Username"
          placeholder="user@gmail.com"
        />
        <BaseInput
          v-model="password"
          label="Password"
          type="password"
          :placeholder="settingsStore.isConfigured(props.device) ? '********' : 'Enter password'"
        />
      </div>

      <div class="grid gap-4 sm:grid-cols-2">
        <BaseInput
          v-model="fromEmail"
          label="From Email"
          placeholder="sender@example.com"
        />
        <BaseInput
          v-model="recipientEmail"
          label="Recipient Email"
          :placeholder="props.device === 'kindle' ? 'your-kindle@kindle.com' : 'your-device@pbsync.com'"
        />
      </div>

      <div class="flex gap-3">
        <BaseButton
          type="submit"
          variant="primary"
          :loading="settingsStore.saving"
          :disabled="settingsStore.saving"
        >
          Save
        </BaseButton>

        <BaseButton
          v-if="settingsStore.isConfigured(props.device)"
          variant="danger"
          :loading="settingsStore.deleting"
          :disabled="settingsStore.deleting"
          @click="handleDelete"
        >
          Delete
        </BaseButton>
      </div>
    </form>
  </div>
</template>
