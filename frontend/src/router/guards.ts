import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { AuthService } from '@/api/generated'

export function setupRouteGuards(router: Router) {
  router.beforeEach(async (to) => {
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

    if (to.meta.checkRegistration) {
      try {
        const status = await AuthService.getRegistrationStatus()
        if (!status.enabled) {
          return { name: 'login' }
        }
      } catch {
        return { name: 'login' }
      }
    }
  })
}
