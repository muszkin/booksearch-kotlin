import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import AdminView from '../AdminView.vue'
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

const mockUsers: UserResponse[] = [
  {
    id: 1,
    email: 'admin@example.com',
    displayName: 'Admin',
    isSuperAdmin: true,
    isActive: true,
    forcePasswordChange: false,
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 2,
    email: 'user@example.com',
    displayName: 'Regular User',
    isSuperAdmin: false,
    isActive: true,
    forcePasswordChange: false,
    createdAt: '2026-01-02T00:00:00Z',
  },
]

describe('AdminView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(AdminService.listUsers).mockReturnValue(resolving(mockUsers))
    vi.mocked(AdminService.toggleRegistration).mockReturnValue(resolving({}))
  })

  it('renders PageHeader with title "Administration"', () => {
    const wrapper = mount(AdminView)
    const header = wrapper.find('header')
    expect(header.exists()).toBe(true)
    expect(header.text()).toContain('Administration')
  })

  it('renders registration toggle switch', () => {
    const wrapper = mount(AdminView)
    const toggle = wrapper.find('[data-testid="registration-toggle"]')
    expect(toggle.exists()).toBe(true)
  })

  it('renders UserTable with users from admin store', async () => {
    const wrapper = mount(AdminView)
    await flushPromises()

    expect(wrapper.text()).toContain('admin@example.com')
    expect(wrapper.text()).toContain('Regular User')
  })

  it('renders InviteUserForm section', () => {
    const wrapper = mount(AdminView)
    expect(wrapper.text()).toContain('Invite User')
  })
})
