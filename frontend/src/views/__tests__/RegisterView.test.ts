import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import RegisterView from '../RegisterView.vue'
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

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/register', name: 'register', component: RegisterView },
      { path: '/login', name: 'login', component: { template: '<div>Login</div>' } },
      { path: '/search', name: 'search', component: { template: '<div>Search</div>' } },
    ],
  })
}

function mountRegisterView() {
  const router = createTestRouter()
  const pinia = createPinia()
  setActivePinia(pinia)

  return {
    wrapper: mount(RegisterView, {
      global: {
        plugins: [pinia, router],
      },
    }),
    router,
    pinia,
  }
}

describe('RegisterView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows validation error when passwords do not match', async () => {
    const { wrapper } = mountRegisterView()
    const authStore = useAuthStore()
    authStore.register = vi.fn()

    const inputs = wrapper.findAll('input')
    const displayNameInput = inputs[0]
    const emailInput = inputs[1]
    const passwordInput = inputs[2]
    const confirmInput = inputs[3]

    await displayNameInput.setValue('Test User')
    await emailInput.setValue('test@example.com')
    await passwordInput.setValue('password123')
    await confirmInput.setValue('different456')
    await wrapper.find('form').trigger('submit')

    await wrapper.vm.$nextTick()

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(authStore.register).not.toHaveBeenCalled()
  })
})
