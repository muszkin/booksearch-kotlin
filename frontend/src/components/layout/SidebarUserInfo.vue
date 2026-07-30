<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const returningToAdmin = ref(false)
const returnError = ref<string | null>(null)

async function handleLogout() {
  await authStore.logout()
  await router.push('/login')
}

async function handleReturnToAdmin() {
  returningToAdmin.value = true
  returnError.value = null
  try {
    await authStore.stopImpersonation()
    await router.replace('/admin')
  } catch (error) {
    returnError.value = 'Could not return to the admin account.'
    // eslint-disable-next-line no-console
    console.error('stopImpersonation failed', error)
  } finally {
    returningToAdmin.value = false
  }
}
</script>

<template>
  <div class="border-t border-zinc-700 px-4 py-4">
    <button
      v-if="authStore.isImpersonating"
      type="button"
      data-testid="return-to-admin-link"
      class="mb-3 inline-flex min-h-8 items-center text-left text-xs font-medium text-amber-400 underline decoration-amber-400/50 underline-offset-4 transition-colors hover:text-amber-300 disabled:cursor-wait disabled:opacity-60"
      :disabled="returningToAdmin"
      @click="handleReturnToAdmin"
    >
      {{ returningToAdmin ? 'Returning to admin…' : 'Return to admin' }}
    </button>

    <p
      v-if="returnError"
      data-testid="return-to-admin-error"
      class="mb-3 text-xs text-rose-400"
    >
      {{ returnError }}
    </p>

    <div class="flex items-center justify-between gap-3">
      <div class="min-w-0">
        <p class="truncate text-sm font-medium text-zinc-100">
          {{ authStore.user?.displayName ?? 'User' }}
        </p>
        <p class="truncate text-xs text-zinc-400">
          {{ authStore.user?.email ?? '' }}
        </p>
        <p
          v-if="authStore.isImpersonating"
          data-testid="admin-impersonation-subtitle"
          class="truncate text-xs text-amber-400"
        >
          🔒 admin: {{ authStore.realAdminEmail }}
        </p>
      </div>
      <button
        type="button"
        class="flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg text-zinc-400 transition-colors hover:bg-zinc-700/50 hover:text-zinc-100"
        aria-label="Logout"
        @click="handleLogout"
      >
        <svg
          class="h-5 w-5"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          stroke-width="1.5"
          stroke="currentColor"
          aria-hidden="true"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            d="M15.75 9V5.25A2.25 2.25 0 0013.5 3h-6a2.25 2.25 0 00-2.25 2.25v13.5A2.25 2.25 0 007.5 21h6a2.25 2.25 0 002.25-2.25V15m3 0l3-3m0 0l-3-3m3 3H9"
          />
        </svg>
      </button>
    </div>
  </div>
</template>
