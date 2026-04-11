import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api',
})

apiClient.interceptors.request.use((config) => {
  config.headers['X-Request-Id'] = crypto.randomUUID()
  return config
})

export default apiClient
