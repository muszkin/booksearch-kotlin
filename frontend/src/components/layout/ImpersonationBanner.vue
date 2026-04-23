<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AlertMessage from '@/components/base/AlertMessage.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const returning = ref(false)
const errorMessage = ref<string | null>(null)

async function handleReturn() {
  returning.value = true
  errorMessage.value = null
  try {
    await authStore.stopImpersonation()
    router.push('/admin')
  } catch (err) {
    errorMessage.value = 'Failed to return to admin account. Please try again.'
    // eslint-disable-next-line no-console
    console.error('stopImpersonation failed', err)
  } finally {
    returning.value = false
  }
}
</script>

<template>
  <div class="sticky top-0 z-[60] w-full" data-testid="impersonation-banner">
    <AlertMessage variant="warning">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="min-w-0 text-sm">
          <span class="mr-1" aria-hidden="true">🔒</span>
          <span>Viewing as </span>
          <span class="font-semibold">{{ authStore.user?.displayName }}</span>
          <span class="text-xs text-amber-200/80"> · {{ authStore.user?.email }}</span>
        </div>
        <BaseButton
          data-testid="return-to-admin-btn"
          variant="ghost"
          :loading="returning"
          :disabled="returning"
          @click="handleReturn"
        >
          Return to admin
        </BaseButton>
      </div>
      <p
        v-if="errorMessage"
        class="mt-2 text-xs text-rose-300"
        data-testid="banner-error"
      >
        {{ errorMessage }}
      </p>
    </AlertMessage>
  </div>
</template>
