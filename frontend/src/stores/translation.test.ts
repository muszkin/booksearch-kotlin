import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { TranslationJobState, TranslationService } from '@/api/generated'
import type { TranslationStatusResponse } from '@/api/generated'
import { useLibraryStore } from './library'
import { useTranslationStore } from './translation'
import { useAuthStore } from './auth'
import type { UserResponse } from '@/api/generated'

const estimate = { totalChapters: 12, estimatedInputTokens: 45000, modelId: 'provider/free', indicativeDuration: 'minutes_to_hours', limitWarning: 'Daily limits apply.' }
const job: TranslationStatusResponse = { jobId: 'job-uuid', sourceLibraryEntryId: 1, status: TranslationJobState.RUNNING, modelId: 'provider/free', totalChapters: 12, completedChapters: 4, estimatedInputTokens: 45000, actualInputTokens: 100, actualOutputTokens: 110, resumable: false }

beforeEach(() => {
  setActivePinia(createPinia())
  window.sessionStorage.clear()
  vi.useFakeTimers()
  vi.spyOn(TranslationService, 'estimateTranslation').mockResolvedValue(estimate)
  vi.spyOn(TranslationService, 'startTranslation').mockResolvedValue({ jobId: job.jobId, status: TranslationJobState.QUEUED })
  vi.spyOn(TranslationService, 'getTranslationStatus').mockResolvedValue(job)
  vi.spyOn(TranslationService, 'resumeTranslation').mockResolvedValue({ jobId: job.jobId, status: TranslationJobState.QUEUED })
  vi.spyOn(TranslationService, 'cancelTranslation').mockResolvedValue(undefined)
  vi.spyOn(useLibraryStore(), 'fetchLibrary').mockResolvedValue(undefined)
})
afterEach(() => {
  useTranslationStore().cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('translation store', () => {
  it('restores saved jobs when the current account finishes loading after the library mounts', async () => {
    const id = '12345678-1234-1234-1234-123456789abc'
    window.sessionStorage.setItem('translation-jobs:7', JSON.stringify([id]))
    const store = useTranslationStore()
    await store.restore()
    useAuthStore().user = { id: 7 } as UserResponse
    await vi.advanceTimersByTimeAsync(0)
    expect(TranslationService.getTranslationStatus).toHaveBeenCalledExactlyOnceWith(id)
  })

  it('ignores a stale estimate failure after the dialog changes to another book', async () => {
    let reject!: (reason: Error) => void
    vi.mocked(TranslationService.estimateTranslation).mockReturnValueOnce(new Promise((_, fail) => { reject = fail }) as ReturnType<typeof TranslationService.estimateTranslation>)
    const store = useTranslationStore()
    const first = store.estimate(1)
    await store.estimate(2)
    reject(new Error('old request failed'))
    await first
    expect(store.error).toBeNull()
    expect(store.estimates.get(2)).toEqual(estimate)
  })

  it('retains recovery UUIDs when one restored status is temporarily unavailable', async () => {
    const ids = ['12345678-1234-1234-1234-123456789abc', '87654321-1234-1234-1234-123456789abc']
    useAuthStore().user = { id: 7 } as UserResponse
    window.sessionStorage.setItem('translation-jobs:7', JSON.stringify(ids))
    vi.mocked(TranslationService.getTranslationStatus)
      .mockResolvedValueOnce({ ...job, jobId: ids[0]!, status: TranslationJobState.PAUSED })
      .mockRejectedValueOnce(new Error('offline'))
    const store = useTranslationStore()
    await store.restore()
    expect(JSON.parse(window.sessionStorage.getItem('translation-jobs:7')!)).toEqual(ids)
    expect(store.jobErrors.get(ids[1]!)).toBeTruthy()
  })

  it('recovers only this account UUIDs, rechecks server status and clears terminal recovery data', async () => {
    const id = '12345678-1234-1234-1234-123456789abc'
    useAuthStore().user = { id: 7 } as UserResponse
    window.sessionStorage.setItem('translation-jobs:7', JSON.stringify([id]))
    window.sessionStorage.setItem('translation-jobs:8', JSON.stringify(['87654321-1234-1234-1234-123456789abc']))
    vi.mocked(TranslationService.getTranslationStatus).mockResolvedValueOnce({ ...job, jobId: id, status: TranslationJobState.PAUSED, resumable: true })
    const store = useTranslationStore()
    await store.restore()
    expect(TranslationService.getTranslationStatus).toHaveBeenCalledExactlyOnceWith(id)
    expect(store.jobs.get(id)?.resumable).toBe(true)
    expect(JSON.parse(window.sessionStorage.getItem('translation-jobs:7')!)).toEqual([id])
    await vi.advanceTimersByTimeAsync(10000)
    expect(TranslationService.getTranslationStatus).toHaveBeenCalledTimes(1)
    await store.cancel(id)
    expect(window.sessionStorage.getItem('translation-jobs:7')).toBeNull()
    expect(window.sessionStorage.getItem('translation-jobs:8')).not.toBeNull()
  })

  it('persists only UUIDs on start and removes recovery data on completion', async () => {
    useAuthStore().user = { id: 7 } as UserResponse
    const store = useTranslationStore()
    await store.estimate(1)
    await store.start(1)
    expect(JSON.parse(window.sessionStorage.getItem('translation-jobs:7')!)).toEqual([job.jobId])
    vi.mocked(TranslationService.getTranslationStatus).mockResolvedValue({ ...job, status: TranslationJobState.COMPLETED })
    await vi.advanceTimersByTimeAsync(5000)
    expect(window.sessionStorage.getItem('translation-jobs:7')).toBeNull()
  })

  it('clears in-memory jobs and timers when the account changes', async () => {
    const auth = useAuthStore()
    auth.user = { id: 7 } as UserResponse
    const store = useTranslationStore()
    await store.estimate(1)
    await store.start(1)
    auth.user = { id: 8 } as UserResponse
    await vi.advanceTimersByTimeAsync(10000)
    expect(store.jobs.size).toBe(0)
    expect(store.estimates.size).toBe(0)
    expect(TranslationService.getTranslationStatus).not.toHaveBeenCalled()
  })

  it('keeps jobs when tokens refresh for the same identity', async () => {
    const auth = useAuthStore()
    auth.user = { id: 7 } as UserResponse
    const store = useTranslationStore()
    await store.estimate(1)
    await store.start(1)
    auth.user = { id: 7, displayName: 'Same account' } as UserResponse
    expect(store.jobs.size).toBe(1)
    await vi.advanceTimersByTimeAsync(5000)
    expect(TranslationService.getTranslationStatus).toHaveBeenCalledOnce()
  })

  it('estimates and starts with confirmed processing, polls once each five seconds without duplicates', async () => {
    const store = useTranslationStore()
    expect(await store.estimate(1)).toEqual(estimate)
    await store.start(1)
    expect(TranslationService.startTranslation).toHaveBeenCalledExactlyOnceWith(1, { externalProcessingConfirmed: true })
    store.poll(job.jobId)
    await vi.advanceTimersByTimeAsync(4999)
    expect(TranslationService.getTranslationStatus).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    expect(TranslationService.getTranslationStatus).toHaveBeenCalledExactlyOnceWith(job.jobId)
    await vi.advanceTimersByTimeAsync(5000)
    expect(TranslationService.getTranslationStatus).toHaveBeenCalledTimes(2)
    expect(store.jobs.get(job.jobId)?.completedChapters).toBe(4)
  })

  it.each([TranslationJobState.PAUSED, TranslationJobState.FAILED, TranslationJobState.CANCELLED, TranslationJobState.COMPLETED])('stops polling at %s and refreshes the library only on completion', async (status) => {
    vi.mocked(TranslationService.getTranslationStatus).mockResolvedValue({ ...job, status })
    const store = useTranslationStore()
    store.poll(job.jobId)
    await vi.advanceTimersByTimeAsync(15000)
    expect(TranslationService.getTranslationStatus).toHaveBeenCalledTimes(1)
    expect(useLibraryStore().fetchLibrary).toHaveBeenCalledTimes(status === TranslationJobState.COMPLETED ? 1 : 0)
  })

  it('resumes only through the server and restarts polling after acceptance', async () => {
    const store = useTranslationStore()
    store.jobs.set(job.jobId, { ...job, status: TranslationJobState.PAUSED, resumable: true })
    await store.resume(job.jobId)
    expect(TranslationService.resumeTranslation).toHaveBeenCalledExactlyOnceWith(job.jobId)
    expect(store.jobs.get(job.jobId)?.status).toBe('queued')
    await vi.advanceTimersByTimeAsync(5000)
    expect(TranslationService.getTranslationStatus).toHaveBeenCalledTimes(1)
  })

  it('leaves rejected resumes paused and reports the failure', async () => {
    vi.mocked(TranslationService.resumeTranslation).mockRejectedValue(new Error('model_no_longer_free'))
    const store = useTranslationStore()
    store.jobs.set(job.jobId, { ...job, status: TranslationJobState.PAUSED, resumable: true })
    await store.resume(job.jobId)
    await vi.advanceTimersByTimeAsync(10000)
    expect(store.jobs.get(job.jobId)?.status).toBe('paused')
    expect(store.jobErrors.get(job.jobId)).toBeTruthy()
    expect(TranslationService.getTranslationStatus).not.toHaveBeenCalled()
  })

  it('ignores a paused status response from before resume and keeps the new polling interval', async () => {
    let resolveStatus!: (value: TranslationStatusResponse) => void
    vi.mocked(TranslationService.getTranslationStatus).mockReturnValueOnce(new Promise<TranslationStatusResponse>((resolve) => { resolveStatus = resolve }) as ReturnType<typeof TranslationService.getTranslationStatus>)
    const store = useTranslationStore()
    const paused = { ...job, status: TranslationJobState.PAUSED, resumable: true }
    store.jobs.set(job.jobId, paused)
    const pendingStatus = store.refreshStatus(job.jobId)
    await store.resume(job.jobId)
    resolveStatus(paused)
    await pendingStatus
    expect(store.jobs.get(job.jobId)?.status).toBe(TranslationJobState.QUEUED)
    await vi.advanceTimersByTimeAsync(5000)
    expect(TranslationService.getTranslationStatus).toHaveBeenCalledTimes(2)
    expect(store.jobs.get(job.jobId)?.status).toBe(TranslationJobState.RUNNING)
  })

  it('does not begin a status read while resume is pending', async () => {
    let resolveResume!: (value: Awaited<ReturnType<typeof TranslationService.resumeTranslation>>) => void
    vi.mocked(TranslationService.resumeTranslation).mockReturnValueOnce(new Promise((resolve) => { resolveResume = resolve }) as ReturnType<typeof TranslationService.resumeTranslation>)
    const store = useTranslationStore()
    store.jobs.set(job.jobId, { ...job, status: TranslationJobState.PAUSED, resumable: true })
    const pendingResume = store.resume(job.jobId)
    await store.refreshStatus(job.jobId)
    expect(TranslationService.getTranslationStatus).not.toHaveBeenCalled()
    resolveResume({ jobId: job.jobId, status: TranslationJobState.QUEUED })
    await pendingResume
    await vi.advanceTimersByTimeAsync(5000)
    expect(TranslationService.getTranslationStatus).toHaveBeenCalledOnce()
  })

  it('cancels a queued job and stops polling', async () => {
    const store = useTranslationStore()
    await store.estimate(1)
    await store.start(1)
    await store.cancel(job.jobId)
    await vi.advanceTimersByTimeAsync(10000)
    expect(TranslationService.cancelTranslation).toHaveBeenCalledExactlyOnceWith(job.jobId)
    expect(store.jobs.get(job.jobId)?.status).toBe('cancelled')
    expect(TranslationService.getTranslationStatus).not.toHaveBeenCalled()
  })

  it('stops polling after status failure and supports an explicit status retry', async () => {
    const store = useTranslationStore()
    vi.mocked(TranslationService.getTranslationStatus).mockRejectedValueOnce(new Error('offline'))
    store.poll(job.jobId)
    await vi.advanceTimersByTimeAsync(10000)
    expect(TranslationService.getTranslationStatus).toHaveBeenCalledTimes(1)
    expect(store.jobErrors.get(job.jobId)).toBeTruthy()
    await store.refreshStatus(job.jobId)
    await vi.advanceTimersByTimeAsync(5000)
    expect(TranslationService.getTranslationStatus).toHaveBeenCalledTimes(3)
  })

  it('does not overlap slow status requests or apply a response after cleanup', async () => {
    let resolve!: (value: TranslationStatusResponse) => void
    vi.mocked(TranslationService.getTranslationStatus).mockReturnValue(new Promise<TranslationStatusResponse>((done) => { resolve = done }) as ReturnType<typeof TranslationService.getTranslationStatus>)
    const store = useTranslationStore()
    store.poll(job.jobId)
    await vi.advanceTimersByTimeAsync(15000)
    expect(TranslationService.getTranslationStatus).toHaveBeenCalledTimes(1)
    store.cleanup()
    resolve({ ...job, status: TranslationJobState.COMPLETED })
    await vi.advanceTimersByTimeAsync(10000)
    expect(useLibraryStore().fetchLibrary).not.toHaveBeenCalled()
    expect(store.jobs.size).toBe(0)
  })
})
