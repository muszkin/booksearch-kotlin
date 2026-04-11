import { describe, it, expect } from 'vitest'
import router from './index'

describe('Vue Router', () => {
  it('has home route defined at root path', () => {
    const homeRoute = router.getRoutes().find((r) => r.path === '/')
    expect(homeRoute).toBeDefined()
    expect(homeRoute?.name).toBe('home')
  })

  it('resolves root path to home route', () => {
    const resolved = router.resolve('/')
    expect(resolved.name).toBe('home')
  })
})
