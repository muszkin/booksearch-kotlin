import { onScopeDispose, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, TranslationJobState, TranslationService } from '@/api/generated'
import type { TranslationEstimateResponse, TranslationStatusResponse } from '@/api/generated'
import { useLibraryStore } from './library'
import { useAuthStore } from './auth'

const POLL_INTERVAL_MS = 5000
const ACTIVE_STATES = new Set([TranslationJobState.QUEUED, TranslationJobState.RUNNING])

export const useTranslationStore = defineStore('translation', () => {
  const library = useLibraryStore()
  const auth = useAuthStore()
  const estimates = ref(new Map<number, TranslationEstimateResponse>())
  const jobs = ref(new Map<string, TranslationStatusResponse>())
  const jobErrors = ref(new Map<string, string>())
  const busyJobs = ref(new Set<string>())
  const loading = ref(false)
  const starting = ref(false)
  const restoring = ref(false)
  const discoveryError = ref<string | null>(null)
  const error = ref<string | null>(null)
  const intervals = new Map<string, ReturnType<typeof setInterval>>()
  const requests = new Map<string, symbol>()
  const recoveryIds = new Set<string>()
  let generation = 0
  let estimateRequest = 0
  let observing = false
  let restoreRequest = 0

  function recoveryKey() {
    if (!auth.user) return null
    return `translation-jobs:${auth.user.id}${auth.isImpersonating ? `:as:${auth.user.actAsUserId}` : ''}`
  }

  function saveRecovery() {
    const key = recoveryKey()
    if (!key) return
    for (const job of jobs.value.values()) {
      if (ACTIVE_STATES.has(job.status) || job.status === TranslationJobState.PAUSED) recoveryIds.add(job.jobId)
      else recoveryIds.delete(job.jobId)
    }
    const ids = [...recoveryIds]
    try {
      if (ids.length) window.sessionStorage.setItem(key, JSON.stringify(ids))
      else window.sessionStorage.removeItem(key)
    } catch {
      // Storage can be unavailable; the server still owns the durable job.
    }
  }

  async function restore() {
    observing = true
    restoring.value = true
    discoveryError.value = null
    const currentGeneration = generation
    const request = ++restoreRequest
    const previousJobs = new Map(jobs.value)
    const ids = new Set(jobs.value.keys())
    const key = recoveryKey()
    if (key) {
      try {
        const saved: unknown = JSON.parse(window.sessionStorage.getItem(key) ?? '[]')
        if (Array.isArray(saved)) {
          for (const id of saved) {
            if (typeof id === 'string' && /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(id)) {
              ids.add(id)
              recoveryIds.add(id)
            }
          }
        }
      } catch {
        // Ignore malformed or inaccessible local recovery data.
      }
    }
    try {
      const found = await TranslationService.listTranslationJobs()
      if (currentGeneration !== generation || request !== restoreRequest) return
      for (const job of found) {
        // A resume, cancellation or status response may have completed during discovery.
        if (jobs.value.get(job.jobId) !== previousJobs.get(job.jobId) || busyJobs.value.has(job.jobId)) continue
        jobs.value.set(job.jobId, job)
        jobErrors.value.delete(job.jobId)
        ids.delete(job.jobId)
        if (ACTIVE_STATES.has(job.status)) poll(job.jobId)
        else stopPolling(job.jobId)
      }
      saveRecovery()
    } catch {
      if (currentGeneration !== generation || request !== restoreRequest) return
      discoveryError.value = 'Could not restore translations. Retry before starting a new translation.'
    } finally {
      if (currentGeneration === generation && request === restoreRequest) restoring.value = false
    }
    if (currentGeneration === generation && request === restoreRequest) await Promise.all([...ids].map(refreshStatus))
  }

  function jobForLibrary(libraryId: number) {
    const matches = [...jobs.value.values()].reverse().filter((job) => job.sourceLibraryEntryId === libraryId)
    return matches.find((job) => ACTIVE_STATES.has(job.status) || job.status === TranslationJobState.PAUSED) ?? matches[0]
  }

  async function estimate(libraryId: number) {
    loading.value = true
    error.value = null
    estimates.value.delete(libraryId)
    const currentGeneration = generation
    const request = ++estimateRequest
    try {
      const result = await TranslationService.estimateTranslation(libraryId)
      if (currentGeneration !== generation || request !== estimateRequest) return null
      estimates.value.set(libraryId, result)
      return result
    } catch (err) {
      if (currentGeneration === generation && request === estimateRequest) {
        error.value = err instanceof ApiError && err.status === 422
          ? 'Translation is not configured or this source/model is not eligible. Ask an administrator to check the free model.'
          : 'Could not estimate translation. Please try again.'
      }
      return null
    } finally {
      if (currentGeneration === generation && request === estimateRequest) loading.value = false
    }
  }

  function stopPolling(jobId: string) {
    const interval = intervals.get(jobId)
    if (interval !== undefined) clearInterval(interval)
    intervals.delete(jobId)
    requests.delete(jobId)
  }

  async function readStatus(jobId: string) {
    if (requests.has(jobId) || busyJobs.value.has(jobId)) return
    const request = Symbol(jobId)
    requests.set(jobId, request)
    try {
      const result = await TranslationService.getTranslationStatus(jobId)
      if (requests.get(jobId) !== request) return
      const previous = jobs.value.get(jobId)
      jobs.value.set(jobId, result)
      saveRecovery()
      jobErrors.value.delete(jobId)
      if (!ACTIVE_STATES.has(result.status)) stopPolling(jobId)
      if (result.status === TranslationJobState.COMPLETED && previous?.status !== TranslationJobState.COMPLETED) {
        await library.fetchLibrary(library.pagination.page)
      }
      return result
    } catch {
      if (requests.get(jobId) === request) {
        stopPolling(jobId)
        jobErrors.value.set(jobId, 'Could not refresh translation status. Retry to reconnect.')
      }
    } finally {
      if (requests.get(jobId) === request) requests.delete(jobId)
    }
  }

  function poll(jobId: string) {
    if (intervals.has(jobId)) return
    const job = jobs.value.get(jobId)
    if (job && !ACTIVE_STATES.has(job.status)) return
    intervals.set(jobId, setInterval(() => { void readStatus(jobId) }, POLL_INTERVAL_MS))
  }

  async function refreshStatus(jobId: string) {
    const currentGeneration = generation
    const job = await readStatus(jobId)
    if (currentGeneration === generation && job && ACTIVE_STATES.has(job.status)) poll(jobId)
  }

  async function start(libraryId: number) {
    const estimate = estimates.value.get(libraryId)
    const existing = jobForLibrary(libraryId)
    if (starting.value || restoring.value || discoveryError.value || !estimate ||
      (existing && (ACTIVE_STATES.has(existing.status) || existing.status === TranslationJobState.PAUSED))) return false
    starting.value = true
    error.value = null
    const currentGeneration = generation
    try {
      const result = await TranslationService.startTranslation(libraryId, { externalProcessingConfirmed: true })
      if (currentGeneration !== generation) return false
      jobs.value.set(result.jobId, {
        ...result, sourceLibraryEntryId: libraryId, modelId: estimate.modelId,
        totalChapters: estimate.totalChapters, estimatedInputTokens: estimate.estimatedInputTokens,
        completedChapters: 0, actualInputTokens: 0, actualOutputTokens: 0, resumable: false,
      })
      saveRecovery()
      poll(result.jobId)
      return true
    } catch (err) {
      if (currentGeneration === generation) {
        if (err instanceof ApiError && err.status === 409) {
          await restore()
          if (currentGeneration !== generation) return false
          const recovered = jobForLibrary(libraryId)
          // Let the dialog close onto the recovered progress controls.
          if (recovered && (ACTIVE_STATES.has(recovered.status) || recovered.status === TranslationJobState.PAUSED)) return true
        }
        error.value = err instanceof ApiError && err.status === 409
          ? 'An active translation already exists for this EPUB.'
          : 'Could not start translation. Check that this EPUB and the configured free model are still available.'
      }
      return false
    } finally {
      if (currentGeneration === generation) starting.value = false
    }
  }

  async function resume(jobId: string) {
    const job = jobs.value.get(jobId)
    if (!job?.resumable || busyJobs.value.has(jobId)) return
    busyJobs.value.add(jobId)
    stopPolling(jobId)
    jobErrors.value.delete(jobId)
    const currentGeneration = generation
    try {
      const result = await TranslationService.resumeTranslation(jobId)
      if (currentGeneration !== generation) return
      jobs.value.set(jobId, { ...job, status: result.status, error: null, resumable: false })
      saveRecovery()
      poll(jobId)
    } catch {
      if (currentGeneration === generation) jobErrors.value.set(jobId, 'Could not resume. The source and original model must still be available and free.')
    } finally {
      if (currentGeneration === generation) busyJobs.value.delete(jobId)
    }
  }

  async function cancel(jobId: string) {
    const job = jobs.value.get(jobId)
    if (!job || ![TranslationJobState.QUEUED, TranslationJobState.PAUSED].includes(job.status) || busyJobs.value.has(jobId)) return
    busyJobs.value.add(jobId)
    jobErrors.value.delete(jobId)
    const currentGeneration = generation
    try {
      await TranslationService.cancelTranslation(jobId)
      if (currentGeneration !== generation) return
      stopPolling(jobId)
      jobs.value.set(jobId, { ...job, status: TranslationJobState.CANCELLED, resumable: false, error: null })
      saveRecovery()
    } catch {
      if (currentGeneration === generation) jobErrors.value.set(jobId, 'Could not cancel. Only queued or paused work can be cancelled. Refresh the status and try again.')
    } finally {
      if (currentGeneration === generation) busyJobs.value.delete(jobId)
    }
  }

  function cleanup() {
    observing = false
    generation++
    for (const jobId of intervals.keys()) stopPolling(jobId)
    requests.clear()
    loading.value = false
    starting.value = false
    restoring.value = false
    discoveryError.value = null
    busyJobs.value.clear()
  }

  // Clear account-owned state when signing out or switching impersonation identity.
  watch([() => auth.user?.id, () => auth.user?.actAsUserId], () => {
    const wasObserving = observing
    cleanup()
    jobs.value.clear()
    recoveryIds.clear()
    estimates.value.clear()
    jobErrors.value.clear()
    error.value = null
    if (wasObserving && auth.user) void restore()
  }, { flush: 'sync' })
  onScopeDispose(cleanup)

  return { estimates, jobs, jobErrors, busyJobs, loading, starting, restoring, discoveryError, error, jobForLibrary, estimate, start, poll, refreshStatus, resume, cancel, stopPolling, restore, cleanup }
})
