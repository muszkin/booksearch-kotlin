<script setup lang="ts">
import { onMounted } from 'vue'
import { useSettingsStore } from '@/stores/settings'
import PageHeader from '@/components/layout/PageHeader.vue'
import SmtpConfigCard from '@/components/settings/SmtpConfigCard.vue'
import ChangePasswordForm from '@/components/settings/ChangePasswordForm.vue'

const settingsStore = useSettingsStore()

onMounted(() => {
  settingsStore.fetchSettings()
})
</script>

<template>
  <PageHeader title="Settings" />

  <div class="space-y-8 p-6">
    <section>
      <h2 class="mb-4 text-base font-semibold text-zinc-200">Device Configuration</h2>
      <div class="grid gap-6 lg:grid-cols-2">
        <SmtpConfigCard device="kindle" title="Kindle" />
        <SmtpConfigCard device="pocketbook" title="PocketBook" />
      </div>
    </section>

    <section>
      <h2 class="mb-4 text-base font-semibold text-zinc-200">Security</h2>
      <div class="max-w-xl">
        <ChangePasswordForm />
      </div>
    </section>
  </div>
</template>
