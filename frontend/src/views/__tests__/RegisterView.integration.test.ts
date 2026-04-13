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
      global: { plugins: [pinia, router] },
    }),
    router,
  }
}

describe('RegisterView integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls authStore.register and redirects to /search on success', async () => {
    const { wrapper, router } = mountRegisterView()
    const authStore = useAuthStore()
    authStore.register = vi.fn().mockResolvedValueOnce(undefined)

    await router.push('/register')
    await router.isReady()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('Test User')
    await inputs[1].setValue('test@example.com')
    await inputs[2].setValue('password123')
    await inputs[3].setValue('password123')
    await wrapper.find('form').trigger('submit')

    await vi.dynamicImportSettled()

    expect(authStore.register).toHaveBeenCalledWith({
      email: 'test@example.com',
      password: 'password123',
      displayName: 'Test User',
    })

    expect(router.currentRoute.value.name).toBe('search')
  })

  it('shows error AlertMessage when registration fails', async () => {
    const { wrapper } = mountRegisterView()
    const authStore = useAuthStore()
    authStore.register = vi.fn().mockRejectedValueOnce(new Error('Email already taken'))

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('Test User')
    await inputs[1].setValue('test@example.com')
    await inputs[2].setValue('password123')
    await inputs[3].setValue('password123')
    await wrapper.find('form').trigger('submit')

    await vi.dynamicImportSettled()
    await wrapper.vm.$nextTick()

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('Email already taken')
  })
})
