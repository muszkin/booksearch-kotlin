import { ref } from 'vue'
import { defineStore } from 'pinia'
import { AdminService } from '@/api/generated'
import type { UserResponse, CreateUserRequest, ChangePasswordRequest } from '@/api/generated'

export const useAdminStore = defineStore('admin', () => {
  const users = ref<UserResponse[]>([])
  const registrationEnabled = ref(false)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchUsers() {
    loading.value = true
    error.value = null

    try {
      users.value = await AdminService.listUsers()
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load users'
      users.value = []
    } finally {
      loading.value = false
    }
  }

  async function toggleRegistration(enabled: boolean) {
    error.value = null

    try {
      await AdminService.toggleRegistration({ enabled })
      registrationEnabled.value = enabled
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to toggle registration'
    }
  }

  async function createUser(data: CreateUserRequest) {
    error.value = null

    try {
      const newUser = await AdminService.createUser(data)
      users.value = [...users.value, newUser]
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to create user'
      throw err
    }
  }

  async function changeUserPassword(userId: number, data: ChangePasswordRequest) {
    error.value = null

    try {
      await AdminService.changeUserPassword(userId, data)
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to change user password'
      throw err
    }
  }

  return {
    users,
    registrationEnabled,
    loading,
    error,
    fetchUsers,
    toggleRegistration,
    createUser,
    changeUserPassword,
  }
})
