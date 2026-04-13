import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import LoginView from '../LoginView.vue'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/client', () => {
  const interceptors = {
    request: { use: vi.fn(), eject: vi.fn() },
    response: { use: vi.fn(), eject: vi.fn() },
  }
  return {
    default: {
      post: vi.fn(),
      interceptors,
      defaults: { headers: { common: {} } },
    },
  }
})

function mountLoginView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: LoginView },
      { path: '/register', name: 'register', component: { template: '<div>Register</div>' } },
      { path: '/search', name: 'search', component: { template: '<div>Search</div>' } },
    ],
  })
  const pinia = createPinia()
  setActivePinia(pinia)

  return {
    wrapper: mount(LoginView, {
      global: { plugins: [pinia, router] },
    }),
  }
}

describe('LoginView client-side validation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows validation error when email is empty', async () => {
    const { wrapper } = mountLoginView()
    const authStore = useAuthStore()
    authStore.login = vi.fn()

    const passwordInput = wrapper.find('input[type="password"]')
    await passwordInput.setValue('password123')
    await wrapper.find('form').trigger('submit')

    await wrapper.vm.$nextTick()

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('Email is required')
    expect(authStore.login).not.toHaveBeenCalled()
  })

  it('shows validation error when password is too short', async () => {
    const { wrapper } = mountLoginView()
    const authStore = useAuthStore()
    authStore.login = vi.fn()

    const emailInput = wrapper.find('input[type="text"], input[type="email"]')
    const passwordInput = wrapper.find('input[type="password"]')
    await emailInput.setValue('user@example.com')
    await passwordInput.setValue('short')
    await wrapper.find('form').trigger('submit')

    await wrapper.vm.$nextTick()

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('at least 8 characters')
    expect(authStore.login).not.toHaveBeenCalled()
  })
})
