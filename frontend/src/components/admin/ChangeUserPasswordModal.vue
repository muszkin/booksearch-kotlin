<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useAdminStore } from '@/stores/admin'
import type { UserResponse } from '@/api/generated'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import AlertMessage from '@/components/base/AlertMessage.vue'

interface Props {
  user: UserResponse | null
  visible: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  close: []
}>()

const adminStore = useAdminStore()

const newPassword = ref('')
const sendLoginLink = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const loading = ref(false)

const MIN_PASSWORD_LENGTH = 8

function resetForm() {
  newPassword.value = ''
  sendLoginLink.value = false
  errorMessage.value = ''
  successMessage.value = ''
}

watch(
  () => props.visible,
  (isVisible) => {
    if (isVisible) {
      resetForm()
    }
  },
)

function handleEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.visible) {
    emit('close')
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleEscape)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscape)
})

async function handleSubmit() {
  if (!props.user) return

  errorMessage.value = ''
  successMessage.value = ''

  if (!newPassword.value) {
    errorMessage.value = 'New password is required'
    return
  }

  if (newPassword.value.length < MIN_PASSWORD_LENGTH) {
    errorMessage.value = 'Password must be at least 8 characters'
    return
  }

  loading.value = true
  try {
    await adminStore.changeUserPassword(props.user.id, {
      newPassword: newPassword.value,
      sendLoginLink: sendLoginLink.value,
    })
    successMessage.value = 'Password changed successfully'
    setTimeout(() => emit('close'), 1500)
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Failed to change password'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible && user"
      class="fixed inset-0 z-50 flex items-center justify-center"
      role="dialog"
      aria-modal="true"
      :aria-label="`Change password for ${user.email}`"
    >
      <div
        class="fixed inset-0 bg-black/60 backdrop-blur-sm"
        @click="emit('close')"
      />

      <div class="relative z-10 w-full max-w-md rounded-lg border border-zinc-700 bg-zinc-800 p-6 shadow-xl">
        <div class="mb-4 flex items-center justify-between">
          <h3 class="text-lg font-medium text-zinc-100">
            Change password for {{ user.email }}
          </h3>
          <button
            type="button"
            class="rounded-lg p-1 text-zinc-400 hover:bg-zinc-700 hover:text-zinc-200"
            aria-label="Close modal"
            @click="emit('close')"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              class="h-5 w-5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              aria-hidden="true"
            >
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
        </div>

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
            v-model="newPassword"
            label="New Password"
            type="password"
            placeholder="At least 8 characters"
            autocomplete="new-password"
          />

          <label class="flex items-center gap-2 text-sm text-zinc-300">
            <input
              v-model="sendLoginLink"
              type="checkbox"
              class="h-4 w-4 rounded border-zinc-600 bg-zinc-700 text-accent focus:ring-accent"
            />
            Send login link to user
          </label>

          <div class="flex gap-3">
            <BaseButton
              type="submit"
              variant="primary"
              :loading="loading"
              :disabled="loading"
            >
              Change Password
            </BaseButton>
            <BaseButton
              variant="secondary"
              @click="emit('close')"
            >
              Cancel
            </BaseButton>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>
