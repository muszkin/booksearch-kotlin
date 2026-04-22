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

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: LoginView },
      { path: '/register', name: 'register', component: { template: '<div>Register</div>' } },
      { path: '/search', name: 'search', component: { template: '<div>Search</div>' } },
      { path: '/library', name: 'library', component: { template: '<div>Library</div>' } },
    ],
  })
}

function mountLoginView() {
  const router = createTestRouter()
  const pinia = createPinia()
  setActivePinia(pinia)

  return {
    wrapper: mount(LoginView, {
      global: {
        plugins: [pinia, router],
      },
    }),
    router,
    pinia,
  }
}

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders email field, password field and submit button', () => {
    const { wrapper } = mountLoginView()

    const form = wrapper.find('form')
    expect(form.exists()).toBe(true)

    const labels = wrapper.findAll('label')
    const labelTexts = labels.map((l) => l.text())
    expect(labelTexts.some((t) => t.toLowerCase().includes('email'))).toBe(true)
    expect(labelTexts.some((t) => t.toLowerCase().includes('pass'))).toBe(true)

    const submitButton = wrapper.find('button[type="submit"]')
    expect(submitButton.exists()).toBe(true)
    expect(submitButton.text().toLowerCase()).toContain('sign in')
  })

  it('calls authStore.login and redirects to /search on success', async () => {
    const { wrapper, router } = mountLoginView()
    const authStore = useAuthStore()
    authStore.login = vi.fn().mockResolvedValueOnce(undefined)

    await router.push('/login')
    await router.isReady()

    const emailInput = wrapper.find('input[type="text"], input[type="email"]')
    const passwordInput = wrapper.find('input[type="password"]')

    await emailInput.setValue('user@example.com')
    await passwordInput.setValue('password123')
    await wrapper.find('form').trigger('submit')

    await vi.dynamicImportSettled()

    expect(authStore.login).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: 'password123',
    })

    expect(router.currentRoute.value.name).toBe('search')
  })

  it('shows error AlertMessage when login fails', async () => {
    const { wrapper } = mountLoginView()
    const authStore = useAuthStore()
    authStore.login = vi.fn().mockRejectedValueOnce(new Error('Invalid credentials'))

    const emailInput = wrapper.find('input[type="text"], input[type="email"]')
    const passwordInput = wrapper.find('input[type="password"]')

    await emailInput.setValue('user@example.com')
    await passwordInput.setValue('password123')
    await wrapper.find('form').trigger('submit')

    await vi.dynamicImportSettled()
    await wrapper.vm.$nextTick()

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('Invalid credentials')
  })

  it('redirects to returnUrl query value on successful login', async () => {
    const { wrapper, router } = mountLoginView()
    const authStore = useAuthStore()
    authStore.login = vi.fn().mockResolvedValueOnce(undefined)

    await router.push({ path: '/login', query: { returnUrl: '/library' } })
    await router.isReady()

    const pushSpy = vi.spyOn(router, 'push')

    const emailInput = wrapper.find('input[type="text"], input[type="email"]')
    const passwordInput = wrapper.find('input[type="password"]')

    await emailInput.setValue('user@example.com')
    await passwordInput.setValue('password123')
    await wrapper.find('form').trigger('submit')

    await vi.dynamicImportSettled()
    await wrapper.vm.$nextTick()

    expect(pushSpy).toHaveBeenCalledWith('/library')
    expect(router.currentRoute.value.path).toBe('/library')
  })

  it.each([
    ['//evil.com', 'protocol-relative URL'],
    ['/\\evil', 'backslash after slash'],
    ['https://evil.com', 'absolute URL'],
    ['evil.com', 'non-slash prefix'],
    ['/goodpath', 'control character'],
  ])('falls back to { name: "search" } for malformed returnUrl=%s (%s)', async (rawReturnUrl) => {
    const { wrapper, router } = mountLoginView()
    const authStore = useAuthStore()
    authStore.login = vi.fn().mockResolvedValueOnce(undefined)

    await router.push({ path: '/login', query: { returnUrl: rawReturnUrl } })
    await router.isReady()

    const pushSpy = vi.spyOn(router, 'push')

    const emailInput = wrapper.find('input[type="text"], input[type="email"]')
    const passwordInput = wrapper.find('input[type="password"]')

    await emailInput.setValue('user@example.com')
    await passwordInput.setValue('password123')
    await wrapper.find('form').trigger('submit')

    await vi.dynamicImportSettled()
    await wrapper.vm.$nextTick()

    expect(pushSpy).toHaveBeenCalledWith({ name: 'search' })
    expect(router.currentRoute.value.name).toBe('search')
  })
})
