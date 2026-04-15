import { createRouter, createWebHistory } from 'vue-router'
import { setupRouteGuards } from './guards'
import MainLayout from '@/components/layout/MainLayout.vue'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import SearchView from '@/views/SearchView.vue'
import LibraryView from '@/views/LibraryView.vue'
import SettingsView from '@/views/SettingsView.vue'
import AdminView from '@/views/AdminView.vue'
import DownloadQueueView from '@/views/DownloadQueueView.vue'
import LogsView from '@/views/LogsView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/search',
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { guest: true },
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: { guest: true, checkRegistration: true },
    },
    {
      path: '/',
      component: MainLayout,
      meta: { requiresAuth: true },
      children: [
        {
          path: 'search',
          name: 'search',
          component: SearchView,
        },
        {
          path: 'library',
          name: 'library',
          component: LibraryView,
        },
        {
          path: 'settings',
          name: 'settings',
          component: SettingsView,
        },
        {
          path: 'downloads',
          name: 'downloads',
          component: DownloadQueueView,
        },
        {
          path: 'logs',
          name: 'logs',
          component: LogsView,
        },
        {
          path: 'admin',
          name: 'admin',
          component: AdminView,
          meta: { requiresSuperAdmin: true },
        },
      ],
    },
  ],
})

setupRouteGuards(router)

export default router
