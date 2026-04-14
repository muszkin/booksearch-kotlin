import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ChangePasswordForm from './ChangePasswordForm.vue'
import { AuthService, CancelablePromise } from '@/api/generated'

vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    AuthService: {
      changeOwnPassword: vi.fn(),
    },
  }
})

function resolving<T>(value: T): CancelablePromise<T> {
  return new CancelablePromise((resolve) => resolve(value))
}

describe('ChangePasswordForm', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows validation error when newPassword !== confirmPassword', async () => {
    const wrapper = mount(ChangePasswordForm)

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('oldpassword1')
    await inputs[1].setValue('newpassword1')
    await inputs[2].setValue('differentpassword')

    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('Passwords do not match')
  })

  it('calls AuthService.changeOwnPassword with correct payload on valid submit', async () => {
    vi.mocked(AuthService.changeOwnPassword).mockReturnValue(resolving({}))

    const wrapper = mount(ChangePasswordForm)

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('oldpassword1')
    await inputs[1].setValue('newpassword1')
    await inputs[2].setValue('newpassword1')

    await wrapper.find('form').trigger('submit')

    expect(AuthService.changeOwnPassword).toHaveBeenCalledWith({
      currentPassword: 'oldpassword1',
      newPassword: 'newpassword1',
    })
  })
})
