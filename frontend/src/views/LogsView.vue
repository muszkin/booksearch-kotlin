<script setup lang="ts">
import { onMounted, computed } from 'vue'
import BaseTabs from '@/components/base/BaseTabs.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import PaginationControls from '@/components/library/PaginationControls.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import { useLogsStore } from '@/stores/logs'
import { useAuthStore } from '@/stores/auth'

const logsStore = useLogsStore()
const authStore = useAuthStore()

const isAdmin = computed(() => authStore.user?.isSuperAdmin === true)

const tabs = computed(() => {
  const base = [{ key: 'activity', label: 'Activity Logs' }]
  if (isAdmin.value) {
    base.push({ key: 'requests', label: 'Request Logs' })
  }
  return base
})

const activityTypeOptions = [
  { value: '', label: 'All types' },
  { value: 'DOWNLOAD_STARTED', label: 'Download' },
  { value: 'BOOK_DELIVERED', label: 'Delivery' },
  { value: 'BOOK_CONVERTED', label: 'Conversion' },
  { value: 'BOOK_REMOVED', label: 'Removal' },
  { value: 'SETTINGS_CHANGED', label: 'Settings' },
]

const methodOptions = [
  { value: '', label: 'All methods' },
  { value: 'GET', label: 'GET' },
  { value: 'POST', label: 'POST' },
  { value: 'PUT', label: 'PUT' },
  { value: 'PATCH', label: 'PATCH' },
  { value: 'DELETE', label: 'DELETE' },
]

function handleTabChange(key: string) {
  logsStore.currentTab = key
  if (key === 'activity') {
    logsStore.fetchActivityLogs(1)
  } else {
    logsStore.fetchRequestLogs(1)
  }
}

function statusCodeColor(code: number): string {
  if (code < 300) return 'text-green-400'
  if (code < 400) return 'text-yellow-400'
  if (code < 500) return 'text-orange-400'
  return 'text-rose-400'
}

function methodColor(method: string): string {
  const colors: Record<string, string> = {
    GET: 'text-blue-400',
    POST: 'text-green-400',
    PUT: 'text-yellow-400',
    PATCH: 'text-orange-400',
    DELETE: 'text-rose-400',
  }
  return colors[method] ?? 'text-zinc-400'
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString()
}

onMounted(() => {
  logsStore.fetchActivityLogs()
})
</script>

<template>
  <div class="flex min-h-full flex-col">
    <PageHeader title="Logs" />

    <BaseTabs
      :tabs="tabs"
      :active-tab="logsStore.currentTab"
      @update:active-tab="handleTabChange"
    />

    <div class="flex-1 p-6">
      <!-- Activity Logs Tab -->
      <template v-if="logsStore.currentTab === 'activity'">
        <div class="mb-4 flex items-center gap-3">
          <BaseSelect
            :model-value="logsStore.activityTypeFilter ?? ''"
            :options="activityTypeOptions"
            label="Filter by type"
            @update:model-value="(val: string) => logsStore.fetchActivityLogs(1, val || undefined)"
          />
        </div>

        <div v-if="logsStore.loading && logsStore.activityLogs.length === 0" class="flex items-center justify-center py-12">
          <svg class="h-8 w-8 animate-spin text-violet-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
          </svg>
        </div>

        <EmptyState
          v-else-if="logsStore.activityLogs.length === 0 && !logsStore.loading"
          title="No activity logs"
          description="No activity logs found for the current filter."
        />

        <div v-else class="overflow-x-auto">
          <table class="w-full text-left text-sm">
            <thead class="border-b border-zinc-700 text-xs uppercase text-zinc-400">
              <tr>
                <th class="px-4 py-3">Type</th>
                <th class="px-4 py-3">Entity</th>
                <th class="px-4 py-3">Details</th>
                <th class="px-4 py-3">Timestamp</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="log in logsStore.activityLogs"
                :key="log.id"
                class="border-b border-zinc-700/50 text-zinc-300"
              >
                <td class="px-4 py-3">
                  <span class="rounded bg-zinc-700 px-2 py-0.5 text-xs font-medium uppercase text-zinc-200">
                    {{ log.actionType }}
                  </span>
                </td>
                <td class="px-4 py-3">{{ log.entityType }}{{ log.entityId ? ` #${log.entityId}` : '' }}</td>
                <td class="px-4 py-3 text-zinc-400">{{ log.details ?? '-' }}</td>
                <td class="px-4 py-3 text-zinc-400">{{ formatDate(log.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <PaginationControls
          v-if="logsStore.totalActivityPages > 1"
          :current-page="logsStore.activityPage"
          :total-pages="logsStore.totalActivityPages"
          @page-change="(p) => logsStore.fetchActivityLogs(p)"
        />
      </template>

      <!-- Request Logs Tab (admin only) -->
      <template v-if="logsStore.currentTab === 'requests' && isAdmin">
        <div class="mb-4 flex flex-wrap items-end gap-3">
          <BaseSelect
            :model-value="logsStore.requestFilters.method ?? ''"
            :options="methodOptions"
            label="Method"
            @update:model-value="(val: string) => logsStore.fetchRequestLogs(1, { ...logsStore.requestFilters, method: val || undefined })"
          />
          <BaseButton variant="ghost" @click="logsStore.clearFilters(); logsStore.fetchRequestLogs(1)">
            Clear filters
          </BaseButton>
        </div>

        <div v-if="logsStore.loading && logsStore.requestLogs.length === 0" class="flex items-center justify-center py-12">
          <svg class="h-8 w-8 animate-spin text-violet-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
          </svg>
        </div>

        <EmptyState
          v-else-if="logsStore.requestLogs.length === 0 && !logsStore.loading"
          title="No request logs"
          description="No request logs found for the current filters."
        />

        <div v-else class="overflow-x-auto">
          <table class="w-full text-left text-sm">
            <thead class="border-b border-zinc-700 text-xs uppercase text-zinc-400">
              <tr>
                <th class="px-4 py-3">Method</th>
                <th class="px-4 py-3">Path</th>
                <th class="px-4 py-3">Status</th>
                <th class="px-4 py-3">Duration</th>
                <th class="px-4 py-3">Timestamp</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="log in logsStore.requestLogs"
                :key="log.id"
                class="border-b border-zinc-700/50 text-zinc-300"
              >
                <td class="px-4 py-3">
                  <span :class="methodColor(log.method)" class="font-mono font-medium">{{ log.method }}</span>
                </td>
                <td class="px-4 py-3 font-mono text-xs">{{ log.path }}</td>
                <td class="px-4 py-3">
                  <span :class="statusCodeColor(log.statusCode)" class="font-medium">{{ log.statusCode }}</span>
                </td>
                <td class="px-4 py-3 text-zinc-400">{{ log.durationMs }}ms</td>
                <td class="px-4 py-3 text-zinc-400">{{ formatDate(log.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <PaginationControls
          v-if="logsStore.totalRequestPages > 1"
          :current-page="logsStore.requestPage"
          :total-pages="logsStore.totalRequestPages"
          @page-change="(p) => logsStore.fetchRequestLogs(p)"
        />
      </template>
    </div>
  </div>
</template>
