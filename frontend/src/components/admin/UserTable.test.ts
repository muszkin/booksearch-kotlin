import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import UserTable from './UserTable.vue'
import type { UserResponse } from '@/api/generated'

const mockUsers: UserResponse[] = [
  {
    id: 1,
    email: 'admin@example.com',
    displayName: 'Admin User',
    isSuperAdmin: true,
    isActive: true,
    forcePasswordChange: false,
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 2,
    email: 'regular@example.com',
    displayName: 'Regular User',
    isSuperAdmin: false,
    isActive: false,
    forcePasswordChange: false,
    createdAt: '2026-01-02T00:00:00Z',
  },
]

describe('UserTable', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders user rows with email, displayName, isSuperAdmin badge, isActive badge', () => {
    const wrapper = mount(UserTable, {
      props: { users: mockUsers },
    })

    expect(wrapper.text()).toContain('admin@example.com')
    expect(wrapper.text()).toContain('Admin User')
    expect(wrapper.text()).toContain('regular@example.com')
    expect(wrapper.text()).toContain('Regular User')

    expect(wrapper.text()).toContain('Super Admin')
    expect(wrapper.text()).toContain('Active')
    expect(wrapper.text()).toContain('Inactive')
  })

  it('emits change-password event with user when action button clicked', async () => {
    const wrapper = mount(UserTable, {
      props: { users: mockUsers },
    })

    const buttons = wrapper.findAll('[data-testid="change-password-btn"]')
    expect(buttons.length).toBeGreaterThan(0)

    await buttons[0].trigger('click')

    expect(wrapper.emitted('change-password')).toBeTruthy()
    expect(wrapper.emitted('change-password')![0]).toEqual([mockUsers[0]])
  })
})
