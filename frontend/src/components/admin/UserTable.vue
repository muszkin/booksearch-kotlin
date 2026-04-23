<script setup lang="ts">
import type { UserResponse } from '@/api/generated'
import EmptyState from '@/components/base/EmptyState.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import { useAuthStore } from '@/stores/auth'

interface Props {
  users: UserResponse[]
  impersonatingIds?: Set<number>
}

const props = withDefaults(defineProps<Props>(), {
  impersonatingIds: () => new Set<number>(),
})

const emit = defineEmits<{
  'change-password': [user: UserResponse]
  impersonate: [userId: number]
}>()

const authStore = useAuthStore()

function formatDate(isoDate: string): string {
  return new Date(isoDate).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

function canImpersonate(user: UserResponse): boolean {
  const currentId = authStore.user?.id
  return user.id !== currentId && !user.isSuperAdmin
}
</script>

<template>
  <div v-if="props.users.length === 0">
    <EmptyState
      title="No users"
      description="No users found in the system."
    />
  </div>

  <div v-else class="overflow-x-auto">
    <table class="w-full text-left text-sm">
      <thead>
        <tr class="border-b border-zinc-700 text-zinc-400">
          <th scope="col" class="px-4 py-3 font-medium">Email</th>
          <th scope="col" class="px-4 py-3 font-medium">Name</th>
          <th scope="col" class="px-4 py-3 font-medium">Role</th>
          <th scope="col" class="px-4 py-3 font-medium">Status</th>
          <th scope="col" class="px-4 py-3 font-medium">Created</th>
          <th scope="col" class="px-4 py-3 font-medium">Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="user in props.users"
          :key="user.id"
          class="border-b border-zinc-700/50 text-zinc-200"
        >
          <td class="px-4 py-3">{{ user.email }}</td>
          <td class="px-4 py-3">{{ user.displayName }}</td>
          <td class="px-4 py-3">
            <span
              v-if="user.isSuperAdmin"
              class="inline-flex items-center gap-1 rounded-full bg-violet-400/10 px-2.5 py-0.5 text-xs font-medium text-violet-400"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                class="h-3 w-3"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                aria-hidden="true"
              >
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
              </svg>
              Super Admin
            </span>
            <span v-else class="text-zinc-500">User</span>
          </td>
          <td class="px-4 py-3">
            <span
              :class="[
                'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
                user.isActive
                  ? 'bg-emerald-400/10 text-emerald-400'
                  : 'bg-rose-400/10 text-rose-400',
              ]"
            >
              {{ user.isActive ? 'Active' : 'Inactive' }}
            </span>
          </td>
          <td class="px-4 py-3 text-zinc-400">{{ formatDate(user.createdAt) }}</td>
          <td class="px-4 py-3">
            <div class="flex flex-wrap gap-2">
              <BaseButton
                data-testid="change-password-btn"
                variant="ghost"
                class="text-xs"
                @click="emit('change-password', user)"
              >
                Change Password
              </BaseButton>
              <BaseButton
                v-if="canImpersonate(user)"
                data-testid="impersonate-btn"
                variant="ghost"
                class="text-xs"
                :loading="props.impersonatingIds.has(user.id)"
                :disabled="!user.isActive"
                :title="!user.isActive ? 'User is inactive' : undefined"
                @click="emit('impersonate', user.id)"
              >
                Impersonate
              </BaseButton>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
