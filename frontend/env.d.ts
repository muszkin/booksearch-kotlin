/// <reference types="vite/client" />

export {}

declare module '*.css' {
  const content: string
  export default content
}

declare module 'vue-router' {
  interface RouteMeta {
    guest?: boolean
    requiresAuth?: boolean
    requiresSuperAdmin?: boolean
    checkRegistration?: boolean
  }
}
