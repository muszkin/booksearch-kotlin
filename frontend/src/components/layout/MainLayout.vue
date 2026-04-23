<script setup lang="ts">
import { ref } from 'vue'
import AppSidebar from './AppSidebar.vue'
import MobileTopBar from './MobileTopBar.vue'
import MobileDrawer from './MobileDrawer.vue'
import ImpersonationBanner from './ImpersonationBanner.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const drawerOpen = ref(false)

function openDrawer() {
  drawerOpen.value = true
}

function closeDrawer() {
  drawerOpen.value = false
}
</script>

<template>
  <div class="min-h-screen bg-zinc-900">
    <ImpersonationBanner v-if="authStore.isImpersonating" />

    <AppSidebar />

    <MobileTopBar @toggle-menu="openDrawer" />
    <MobileDrawer :open="drawerOpen" @close="closeDrawer" />

    <main class="pt-14 lg:ml-64 lg:pt-0">
      <router-view />
    </main>
  </div>
</template>
