<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import BaseTabs from '@/components/base/BaseTabs.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import PaginationControls from '@/components/library/PaginationControls.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import FormatBadge from '@/components/search/FormatBadge.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import { useDownloadQueueStore } from '@/stores/download-queue'

const store = useDownloadQueueStore()

const tabs = [
  { key: 'active', label: 'Active' },
  { key: 'queued', label: 'Queued' },
  { key: 'completed', label: 'Completed' },
  { key: 'failed', label: 'Failed' },
]

function statusColor(status: string): string {
  const colors: Record<string, string> = {
    active: 'text-blue-400',
    queued: 'text-yellow-400',
    completed: 'text-green-400',
    failed: 'text-rose-400',
    cancelled: 'text-zinc-400',
  }
  return colors[status] ?? 'text-zinc-400'
}

function isCancellable(status: string): boolean {
  return status === 'active' || status === 'queued'
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString()
}

onMounted(() => {
  store.fetchJobs()
  store.startPolling()
})

onUnmounted(() => {
  store.stopPolling()
})
</script>

<template>
  <div class="flex min-h-full flex-col">
    <PageHeader title="Downloads" />

    <BaseTabs
      :tabs="tabs"
      :active-tab="store.currentTab"
      @update:active-tab="store.setTab"
    />

    <div class="flex-1 p-6">
      <div v-if="store.loading && store.jobs.length === 0" class="flex items-center justify-center py-12">
        <svg class="h-8 w-8 animate-spin text-violet-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
        </svg>
      </div>

      <EmptyState
        v-else-if="store.isEmpty"
        title="No download jobs"
        description="There are no download jobs matching this filter."
      />

      <div v-else class="space-y-3">
        <div
          v-for="job in store.jobs"
          :key="job.jobId"
          class="flex items-center justify-between rounded-lg border border-zinc-700 bg-zinc-800 p-4"
        >
          <div class="flex flex-col gap-1">
            <div class="flex items-center gap-3">
              <span class="text-sm font-medium text-zinc-100">{{ job.bookMd5 }}</span>
              <FormatBadge :format="job.format" />
            </div>
            <div class="flex items-center gap-3 text-xs text-zinc-400">
              <span :class="statusColor(job.status)" class="font-medium uppercase">{{ job.status }}</span>
              <span v-if="job.status === 'active'">{{ job.progress }}%</span>
              <span>{{ formatDate(job.createdAt) }}</span>
            </div>
            <p v-if="job.error" class="text-xs text-rose-400">{{ job.error }}</p>
          </div>

          <div class="flex items-center gap-2">
            <div v-if="job.status === 'active'" class="mr-2 h-2 w-24 overflow-hidden rounded-full bg-zinc-700">
              <div
                class="h-full rounded-full bg-violet-400 transition-all"
                :style="{ width: `${job.progress}%` }"
              />
            </div>

            <BaseButton
              v-if="isCancellable(job.status)"
              variant="danger"
              :data-testid="`cancel-job-${job.jobId}`"
              @click="store.cancelJob(job.jobId)"
            >
              Cancel
            </BaseButton>
          </div>
        </div>
      </div>

      <PaginationControls
        v-if="store.totalPages > 1"
        :current-page="store.page"
        :total-pages="store.totalPages"
        @page-change="(p) => store.fetchJobs(undefined, p)"
      />
    </div>
  </div>
</template>
