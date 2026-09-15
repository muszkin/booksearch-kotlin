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
      props: { status: 'failed', progress: 40, error: 'Download timeout' },
    })

    expect(wrapper.text()).toMatch(/failed/i)
    expect(wrapper.text()).toContain('Download timeout')

    const bar = wrapper.find('[role="progressbar"]')
    expect(bar.exists()).toBe(true)
    expect(bar.attributes('aria-valuenow')).toBe('40')
    expect(bar.find('div').attributes('style')).toContain('width: 40%')
    expect(wrapper.html()).toContain('red-400')
  })

  it('uses readable labels for source-specific progress stages', () => {
    const wrapper = mount(DownloadProgressBar, {
      props: { status: 'fetching_slow_download', progress: 40 },
    })

    expect(wrapper.text()).toContain('Waiting for download link')
    expect(wrapper.text()).toContain('40%')
  })

  it('shows when the member JSON API is transferring the file', () => {
    const wrapper = mount(DownloadProgressBar, {
      props: { status: 'downloading_fast_download', progress: 30 },
    })

    expect(wrapper.text()).toContain('Downloading through member API')
    expect(wrapper.text()).toContain('30%')
  })

  it('explains torrent fallback states', () => {
    const wrapper = mount(DownloadProgressBar, {
      props: { status: 'waiting_for_torrent_peers', progress: 50 },
    })

    expect(wrapper.text()).toContain('Waiting for torrent peers')
    expect(wrapper.text()).toContain('50%')
  })

  it('explains that the network route is changing after a challenge', () => {
    const wrapper = mount(DownloadProgressBar, {
      props: { status: 'switching_egress', progress: 20 },
    })

    expect(wrapper.text()).toContain('Changing network route after browser verification')
  })
})
