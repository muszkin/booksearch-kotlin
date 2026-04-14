<script setup lang="ts">
import { ref } from 'vue'
import { useAdminStore } from '@/stores/admin'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import AlertMessage from '@/components/base/AlertMessage.vue'

const adminStore = useAdminStore()

const email = ref('')
const displayName = ref('')
const password = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const loading = ref(false)

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const MIN_PASSWORD_LENGTH = 8

function validateForm(): string | null {
  if (!email.value.trim()) return 'Email is required'
  if (!EMAIL_PATTERN.test(email.value.trim())) return 'Invalid email format'
  if (!displayName.value.trim()) return 'Display name is required'
  if (!password.value) return 'Password is required'
  if (password.value.length < MIN_PASSWORD_LENGTH) return 'Password must be at least 8 characters'
  return null
}

function clearForm() {
  email.value = ''
  displayName.value = ''
  password.value = ''
}

async function handleSubmit() {
  errorMessage.value = ''
  successMessage.value = ''

  const validationError = validateForm()
  if (validationError) {
    errorMessage.value = validationError
    return
  }

  loading.value = true
  try {
    await adminStore.createUser({
      email: email.value.trim(),
      displayName: displayName.value.trim(),
      password: password.value,
    })
    successMessage.value = 'User created successfully'
    clearForm()
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Failed to create user'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="rounded-lg border border-zinc-700 bg-zinc-800 p-6">
    <h3 class="mb-4 text-lg font-medium text-zinc-100">Invite User</h3>

    <AlertMessage
      v-if="errorMessage"
      variant="error"
      :message="errorMessage"
      class="mb-4"
    />

    <AlertMessage
      v-if="successMessage"
      variant="success"
      :message="successMessage"
      class="mb-4"
    />

    <form class="flex flex-col gap-4" @submit.prevent="handleSubmit">
      <BaseInput
        v-model="email"
        label="Email"
        type="text"
        placeholder="user@example.com"
        autocomplete="email"
      />

      <BaseInput
        v-model="displayName"
        label="Display Name"
        placeholder="John Doe"
      />

      <BaseInput
        v-model="password"
        label="Temporary Password"
        type="password"
        placeholder="At least 8 characters"
        autocomplete="new-password"
      />

      <BaseButton
        type="submit"
        variant="primary"
        :loading="loading"
        :disabled="loading"
        class="self-start"
      >
        Create User
      </BaseButton>
    </form>
  </div>
</template>
