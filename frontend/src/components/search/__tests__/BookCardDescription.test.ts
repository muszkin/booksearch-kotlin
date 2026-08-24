import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BookCard from '../BookCard.vue'
import { BookResult } from '@/api/generated'

const book: BookResult = {
  md5: 'abc123',
  title: 'Solaris',
  author: 'Stanisław Lem',
  language: 'Polish [pl]',
  format: 'epub',
  fileSize: '1.2 MB',
  detailUrl: '/d',
  coverUrl: '/c',
  publisher: 'WL',
  year: '1961',
  description: '',
  matchType: BookResult.matchType.NONE,
  ownedFormats: [],
}

const BLURB = 'Stacja badawcza na orbicie myślącego oceanu, który odpowiada uczonym wspomnieniami.'

function mountCard(propsOverrides = {}) {
  return mount(BookCard, {
    props: { book, selected: false, ...propsOverrides },
  })
}

describe('BookCard description', () => {
  it('offers a control to reveal the description', () => {
    const wrapper = mountCard()

    expect(wrapper.find('[data-testid="description-toggle"]').exists()).toBe(true)
  })

  it('asks the parent to load the description when revealed', async () => {
    const wrapper = mountCard()

    await wrapper.get('[data-testid="description-toggle"]').trigger('click')

    expect(wrapper.emitted('toggle-description')).toHaveLength(1)
  })

  it('shows nothing about the description until it is revealed', () => {
    const wrapper = mountCard({ description: BLURB, descriptionSource: 'annas-archive' })

    expect(wrapper.find('[data-testid="description-body"]').exists()).toBe(false)
  })

  it('shows the publisher blurb without any provenance label', async () => {
    const wrapper = mountCard({
      descriptionOpen: true,
      description: BLURB,
      descriptionSource: 'annas-archive',
    })

    expect(wrapper.get('[data-testid="description-body"]').text()).toContain('Stacja badawcza')
    expect(wrapper.find('[data-testid="description-generated-label"]').exists()).toBe(false)
  })

  it('marks a generated description as generated', async () => {
    const wrapper = mountCard({
      descriptionOpen: true,
      description: BLURB,
      descriptionSource: 'openrouter',
    })

    expect(wrapper.get('[data-testid="description-generated-label"]').text()).toMatch(/AI/i)
  })

  it('shows progress while the description is being fetched', () => {
    const wrapper = mountCard({ descriptionOpen: true, descriptionLoading: true })

    expect(wrapper.get('[data-testid="description-body"]').attributes('aria-busy')).toBe('true')
  })

  it('says plainly when no description could be found', () => {
    const wrapper = mountCard({ descriptionOpen: true, descriptionMissing: true })

    expect(wrapper.get('[data-testid="description-body"]').text()).toContain('No description')
  })

  it('shows the isbn when one is known', () => {
    const wrapper = mountCard({
      descriptionOpen: true,
      description: BLURB,
      descriptionSource: 'annas-archive',
      isbn: '9788308068069',
    })

    expect(wrapper.get('[data-testid="description-body"]').text()).toContain('9788308068069')
  })
})
