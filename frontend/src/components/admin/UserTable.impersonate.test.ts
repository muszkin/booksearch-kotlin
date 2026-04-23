import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import UserTable from './UserTable.vue'
import { useAuthStore } from '@/stores/auth'
import type { UserResponse } from '@/api/generated'

const currentAdmin: UserResponse = {
  id: 1,
  email: 'admin@example.com',
  displayName: 'Admin User',
  isSuperAdmin: true,
  isActive: true,
  forcePasswordChange: false,
  createdAt: '2026-01-01T00:00:00Z',
}

const regularActive: UserResponse = {
  id: 2,
  email: 'jan@example.com',
  displayName: 'Jan Kowalski',
  isSuperAdmin: false,
  isActive: true,
  forcePasswordChange: false,
  createdAt: '2026-01-02T00:00:00Z',
}

const otherSuperAdmin: UserResponse = {
  id: 3,
  email: 'sa2@example.com',
  displayName: 'Other SA',
  isSuperAdmin: true,
  isActive: true,
  forcePasswordChange: false,
  createdAt: '2026-01-03T00:00:00Z',
}

const inactiveRegular: UserResponse = {
  id: 4,
  email: 'inactive@example.com',
  displayName: 'Inactive User',
  isSuperAdmin: false,
  isActive: false,
  forcePasswordChange: false,
  createdAt: '2026-01-04T00:00:00Z',
}

const allUsers: UserResponse[] = [currentAdmin, regularActive, otherSuperAdmin, inactiveRegular]

function seedAdmin() {
  const authStore = useAuthStore()
  authStore.user = { ...currentAdmin }
}

describe('UserTable — impersonate column', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders Impersonate button per-row with correct visibility/disabled state', () => {
    seedAdmin()

    const wrapper = mount(UserTable, {
      props: { users: allUsers },
    })

    const rows = wrapper.findAll('tbody tr')
    expect(rows.length).toBe(4)

    const btnSelf = rows[0].find('[data-testid="impersonate-btn"]')
    expect(btnSelf.exists()).toBe(false)

    const btnRegular = rows[1].find('[data-testid="impersonate-btn"]')
    expect(btnRegular.exists()).toBe(true)
    expect((btnRegular.element as HTMLButtonElement).disabled).toBe(false)

    const btnOtherSA = rows[2].find('[data-testid="impersonate-btn"]')
    expect(btnOtherSA.exists()).toBe(false)

    const btnInactive = rows[3].find('[data-testid="impersonate-btn"]')
    expect(btnInactive.exists()).toBe(true)
    expect((btnInactive.element as HTMLButtonElement).disabled).toBe(true)
  })

  it('emits impersonate event with userId when button is clicked', async () => {
    seedAdmin()

    const wrapper = mount(UserTable, {
      props: { users: [currentAdmin, regularActive] },
    })

    const button = wrapper.findAll('[data-testid="impersonate-btn"]')[0]
    expect(button).toBeDefined()
    await button.trigger('click')

    expect(wrapper.emitted('impersonate')).toBeTruthy()
    expect(wrapper.emitted('impersonate')![0]).toEqual([2])
  })
})
