import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LibraryBookCardSkeleton from './LibraryBookCardSkeleton.vue'

describe('LibraryBookCardSkeleton', () => {
  it('renders skeleton placeholder elements for cover, title, author, and buttons', () => {
    const wrapper = mount(LibraryBookCardSkeleton)

    const pulseElements = wrapper.findAll('.animate-pulse')
    expect(pulseElements.length).toBeGreaterThanOrEqual(4)

    expect(wrapper.html()).toContain('w-20')
    expect(wrapper.html()).toContain('h-4')
    expect(wrapper.html()).toContain('h-3')
  })

  it('has correct aria-label for accessibility', () => {
    const wrapper = mount(LibraryBookCardSkeleton)

    expect(wrapper.attributes('aria-label')).toBe('Loading book')
    expect(wrapper.attributes('role')).toBe('status')
  })
})
