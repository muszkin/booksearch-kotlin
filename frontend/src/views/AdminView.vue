<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAdminStore } from '@/stores/admin'
import type { UserResponse } from '@/api/generated'
import PageHeader from '@/components/layout/PageHeader.vue'
import UserTable from '@/components/admin/UserTable.vue'
import InviteUserForm from '@/components/admin/InviteUserForm.vue'
import ChangeUserPasswordModal from '@/components/admin/ChangeUserPasswordModal.vue'
import AlertMessage from '@/components/base/AlertMessage.vue'

const adminStore = useAdminStore()

const selectedUser = ref<UserResponse | null>(null)
const modalVisible = ref(false)

function openPasswordModal(user: UserResponse) {
  selectedUser.value = user
  modalVisible.value = true
}

function closePasswordModal() {
  selectedUser.value = null
  modalVisible.value = false
}

async function handleToggleRegistration() {
  await adminStore.toggleRegistration(!adminStore.registrationEnabled)
}

onMounted(() => {
  adminStore.fetchUsers()
})
</script>

<template>
  <PageHeader title="Administration" />

  <div class="space-y-8 p-6">
    <AlertMessage
      v-if="adminStore.error"
      variant="error"
      :message="adminStore.error"
      class="mb-4"
    />

    <section>
      <h2 class="mb-4 text-base font-semibold text-zinc-200">Registration</h2>
      <div class="flex items-center gap-3">
        <button
          data-testid="registration-toggle"
          type="button"
          role="switch"
          :aria-checked="adminStore.registrationEnabled"
          :class="[
            'relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-violet-400 focus:ring-offset-2 focus:ring-offset-zinc-900',
            adminStore.registrationEnabled ? 'bg-violet-500' : 'bg-zinc-600',
          ]"
          @click="handleToggleRegistration"
        >
          <span
            :class="[
              'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out',
              adminStore.registrationEnabled ? 'translate-x-5' : 'translate-x-0',
            ]"
          />
        </button>
        <span class="text-sm text-zinc-300">
          {{ adminStore.registrationEnabled ? 'Registration enabled' : 'Registration disabled' }}
        </span>
      </div>
    </section>

    <section>
      <h2 class="mb-4 text-base font-semibold text-zinc-200">Users</h2>
      <div v-if="adminStore.loading" class="flex items-center justify-center py-12">
        <div class="h-8 w-8 animate-spin rounded-full border-2 border-zinc-600 border-t-violet-400" aria-label="Loading users" />
      </div>
      <UserTable
        v-else
        :users="adminStore.users"
        @change-password="openPasswordModal"
      />
    </section>

    <section>
      <h2 class="mb-4 text-base font-semibold text-zinc-200">Create User</h2>
      <div class="max-w-xl">
        <InviteUserForm />
      </div>
    </section>

    <ChangeUserPasswordModal
      :user="selectedUser"
      :visible="modalVisible"
      @close="closePasswordModal"
    />
  </div>
</template>
