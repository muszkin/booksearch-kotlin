import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAdminStore } from './admin'
import { AdminService, CancelablePromise } from '@/api/generated'
import type { UserResponse } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    AdminService: {
      listUsers: vi.fn(),
      toggleRegistration: vi.fn(),
      createUser: vi.fn(),
      changeUserPassword: vi.fn(),
    },
  }
})

function resolving<T>(value: T): CancelablePromise<T> {
  return new CancelablePromise((resolve) => resolve(value))
}

const mockUser: UserResponse = {
  id: 1,
  email: 'admin@example.com',
  displayName: 'Admin',
  isSuperAdmin: true,
  isActive: true,
  forcePasswordChange: false,
  createdAt: '2026-01-01T00:00:00Z',
}

const mockUser2: UserResponse = {
  id: 2,
  email: 'user@example.com',
  displayName: 'Regular User',
  isSuperAdmin: false,
  isActive: true,
  forcePasswordChange: false,
  createdAt: '2026-01-02T00:00:00Z',
}

describe('useAdminStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchUsers calls AdminService.listUsers and populates users array', async () => {
    vi.mocked(AdminService.listUsers).mockReturnValue(resolving([mockUser, mockUser2]))

    const store = useAdminStore()
    await store.fetchUsers()

    expect(AdminService.listUsers).toHaveBeenCalledOnce()
    expect(store.users).toHaveLength(2)
    expect(store.users[0].email).toBe('admin@example.com')
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('toggleRegistration calls AdminService.toggleRegistration with correct payload', async () => {
    vi.mocked(AdminService.toggleRegistration).mockReturnValue(resolving({}))

    const store = useAdminStore()
    await store.toggleRegistration(true)

    expect(AdminService.toggleRegistration).toHaveBeenCalledWith({ enabled: true })
    expect(store.registrationEnabled).toBe(true)
  })

  it('createUser calls AdminService.createUser and appends to users list', async () => {
    vi.mocked(AdminService.createUser).mockReturnValue(resolving(mockUser2))

    const store = useAdminStore()
    store.users = [mockUser]

    const data = { email: 'user@example.com', password: 'password123', displayName: 'Regular User' }
    await store.createUser(data)

    expect(AdminService.createUser).toHaveBeenCalledWith(data)
    expect(store.users).toHaveLength(2)
    expect(store.users[1].email).toBe('user@example.com')
  })

  it('changeUserPassword calls AdminService.changeUserPassword with correct args', async () => {
    vi.mocked(AdminService.changeUserPassword).mockReturnValue(resolving({}))

    const store = useAdminStore()
    await store.changeUserPassword(1, { newPassword: 'newpass123' })

    expect(AdminService.changeUserPassword).toHaveBeenCalledWith(1, { newPassword: 'newpass123' })
  })
})
