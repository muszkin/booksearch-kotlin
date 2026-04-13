import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BookCardSkeleton from '../BookCardSkeleton.vue'

describe('BookCardSkeleton', () => {
  it('renders pulsing placeholder blocks with correct styling', () => {
    const wrapper = mount(BookCardSkeleton)

    const pulsingBlocks = wrapper.findAll('.animate-pulse')
    expect(pulsingBlocks.length).toBeGreaterThanOrEqual(3)

    const zincBlocks = wrapper.findAll('.bg-zinc-700')
    expect(zincBlocks.length).toBeGreaterThanOrEqual(3)

    const card = wrapper.find('[data-testid="skeleton-card"]')
    expect(card.classes()).toContain('bg-zinc-800')
    expect(card.classes()).toContain('rounded-lg')
  })
})
