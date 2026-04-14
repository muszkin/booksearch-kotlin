import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

export function setupRouteGuards(router: Router) {
  router.beforeEach((to) => {
    const authStore = useAuthStore()
    authStore.restoreSession()

    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
      return { name: 'login' }
    }

    if (to.meta.guest && authStore.isAuthenticated) {
      return { name: 'search' }
    }

    if (to.meta.requiresSuperAdmin && !authStore.user?.isSuperAdmin) {
      return { name: 'settings' }
    }
  })
}
