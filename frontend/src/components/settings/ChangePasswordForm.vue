<script setup lang="ts">
import { ref } from 'vue'
import { AuthService } from '@/api/generated'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import AlertMessage from '@/components/base/AlertMessage.vue'

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const loading = ref(false)

function validateForm(): string | null {
  if (!currentPassword.value) return 'Current password is required'
  if (!newPassword.value) return 'New password is required'
  if (newPassword.value.length < 8) return 'New password must be at least 8 characters'
  if (newPassword.value !== confirmPassword.value) return 'Passwords do not match'
  return null
}

function clearForm() {
  currentPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
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
    await AuthService.changeOwnPassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    })
    successMessage.value = 'Password changed successfully'
    clearForm()
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Failed to change password'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="rounded-lg border border-zinc-700 bg-zinc-800 p-6">
    <h3 class="mb-4 text-lg font-medium text-zinc-100">Change Password</h3>

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
        v-model="currentPassword"
        label="Current Password"
        type="password"
        placeholder="Enter current password"
        autocomplete="current-password"
      />

      <BaseInput
        v-model="newPassword"
        label="New Password"
        type="password"
        placeholder="At least 8 characters"
        autocomplete="new-password"
      />

      <BaseInput
        v-model="confirmPassword"
        label="Confirm New Password"
        type="password"
        placeholder="Repeat new password"
        autocomplete="new-password"
      />

      <BaseButton
        type="submit"
        variant="primary"
        :loading="loading"
        :disabled="loading"
        class="self-start"
      >
        Change Password
      </BaseButton>
    </form>
  </div>
</template>
