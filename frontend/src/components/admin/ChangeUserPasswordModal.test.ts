import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ChangeUserPasswordModal from './ChangeUserPasswordModal.vue'
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

const teleportStub = { Teleport: true }

describe('ChangeUserPasswordModal', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders user email in modal title', () => {
    const wrapper = mount(ChangeUserPasswordModal, {
      props: { user: mockUser, visible: true },
      global: { stubs: teleportStub },
    })

    expect(wrapper.text()).toContain('admin@example.com')
  })

  it('calls admin store changeUserPassword with correct userId and payload on submit', async () => {
    vi.mocked(AdminService.changeUserPassword).mockReturnValue(resolving({}))

    const wrapper = mount(ChangeUserPasswordModal, {
      props: { user: mockUser, visible: true },
      global: { stubs: teleportStub },
    })

    const passwordInput = wrapper.find('input[type="password"]')
    await passwordInput.setValue('newpassword123')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(AdminService.changeUserPassword).toHaveBeenCalledWith(1, {
      newPassword: 'newpassword123',
      sendLoginLink: false,
    })
  })
})
