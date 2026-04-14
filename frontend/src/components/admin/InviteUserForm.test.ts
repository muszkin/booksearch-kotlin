import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import InviteUserForm from './InviteUserForm.vue'
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

describe('InviteUserForm', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('validates required fields (email, displayName, password)', async () => {
    const wrapper = mount(InviteUserForm)

    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('Email is required')
  })

  it('calls admin store createUser on valid submit', async () => {
    const mockCreatedUser: UserResponse = {
      id: 3,
      email: 'new@example.com',
      displayName: 'New User',
      isSuperAdmin: false,
      isActive: true,
      forcePasswordChange: false,
      createdAt: '2026-04-14T00:00:00Z',
    }
    vi.mocked(AdminService.createUser).mockReturnValue(resolving(mockCreatedUser))

    const wrapper = mount(InviteUserForm)

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('new@example.com')
    await inputs[1].setValue('New User')
    await inputs[2].setValue('password123')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(AdminService.createUser).toHaveBeenCalledWith({
      email: 'new@example.com',
      displayName: 'New User',
      password: 'password123',
    })
  })
})
