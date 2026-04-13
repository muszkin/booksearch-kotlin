<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AuthLayout from '@/components/layout/AuthLayout.vue'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import AlertMessage from '@/components/base/AlertMessage.vue'

const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const errorMessage = ref('')
const loading = ref(false)

function validateForm(): string | null {
  if (!email.value.trim()) {
    return 'Email is required.'
  }
  const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailPattern.test(email.value)) {
    return 'Please enter a valid email address.'
  }
  if (!password.value) {
    return 'Password is required.'
  }
  if (password.value.length < 8) {
    return 'Password must be at least 8 characters.'
  }
  return null
}

async function handleSubmit() {
  errorMessage.value = ''

  const validationError = validateForm()
  if (validationError) {
    errorMessage.value = validationError
    return
  }

  loading.value = true
  try {
    await authStore.login({ email: email.value, password: password.value })
    await router.push({ name: 'search' })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Login failed. Please try again.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <h1 class="mb-6 text-center text-xl font-semibold text-zinc-100">
      Sign in to your account
    </h1>

    <AlertMessage
      v-if="errorMessage"
      variant="error"
      :message="errorMessage"
      class="mb-4"
    />

    <form class="flex flex-col gap-4" @submit.prevent="handleSubmit">
      <BaseInput
        v-model="email"
        label="Email"
        type="text"
        placeholder="you@example.com"
        autocomplete="email"
      />

      <BaseInput
        v-model="password"
        label="Password"
        type="password"
        placeholder="Enter your password"
        autocomplete="current-password"
      />

      <BaseButton
        type="submit"
        variant="primary"
        :loading="loading"
        :disabled="loading"
        class="mt-2 w-full"
      >
        Sign in
      </BaseButton>
    </form>

    <p class="mt-6 text-center text-sm text-zinc-400">
      Don't have an account?
      <RouterLink
        :to="{ name: 'register' }"
        class="font-medium text-sky-300 hover:text-sky-200"
      >
        Create one
      </RouterLink>
    </p>
  </AuthLayout>
</template>
