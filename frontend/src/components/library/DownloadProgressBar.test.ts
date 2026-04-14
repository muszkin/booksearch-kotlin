import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DownloadProgressBar from './DownloadProgressBar.vue'

describe('DownloadProgressBar', () => {
  it('renders progress percentage and status text for downloading state', () => {
    const wrapper = mount(DownloadProgressBar, {
      props: { status: 'downloading', progress: 65 },
    })

    const progressbar = wrapper.find('[role="progressbar"]')
    expect(progressbar.exists()).toBe(true)
    expect(progressbar.attributes('aria-valuenow')).toBe('65')
    expect(progressbar.attributes('aria-valuemin')).toBe('0')
    expect(progressbar.attributes('aria-valuemax')).toBe('100')
    expect(wrapper.text()).toContain('65%')
    expect(wrapper.text()).toMatch(/downloading/i)
  })

  it('renders queued state without percentage', () => {
    const wrapper = mount(DownloadProgressBar, {
      props: { status: 'queued', progress: 0 },
    })

    expect(wrapper.text()).toMatch(/queued/i)
    expect(wrapper.text()).not.toContain('%')
  })

  it('renders failed state with error styling', () => {
    const wrapper = mount(DownloadProgressBar, {
      props: { status: 'failed', progress: 0, error: 'Download timeout' },
    })

    expect(wrapper.text()).toMatch(/failed/i)
    expect(wrapper.text()).toContain('Download timeout')

    const bar = wrapper.find('[role="progressbar"]')
    expect(bar.exists()).toBe(true)
    expect(wrapper.html()).toContain('red-400')
  })
})
