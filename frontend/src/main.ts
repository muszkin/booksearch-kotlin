import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { OpenAPI } from './api/generated'
import './assets/main.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

OpenAPI.TOKEN = async () => localStorage.getItem('accessToken') ?? ''

const authStore = useAuthStore()
authStore.restoreSession()

if (authStore.isAuthenticated && authStore.user === null) {
  authStore.loadCurrentUser().catch(() => {})
}

app.mount('#app')
