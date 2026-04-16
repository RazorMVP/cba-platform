// web-react/src/core/api/cardApiClient.ts
// Separate Axios instance for card-service (port 8081).
// Shares the same auth interceptor pattern as apiClient.
import axios from 'axios'

function getKeycloakToken(): string | null {
  if (typeof window === 'undefined') return null
  return (window as Window & { __keycloakToken?: string }).__keycloakToken ?? null
}

export const cardApiClient = axios.create({
  baseURL: import.meta.env.VITE_CARD_API_URL ?? 'http://localhost:8081',
  headers: { 'Content-Type': 'application/json' },
})

cardApiClient.interceptors.request.use(config => {
  const isBypass = import.meta.env.VITE_AUTH_BYPASS === 'true'
  const token = isBypass ? 'dev-bypass-token' : getKeycloakToken()
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

cardApiClient.interceptors.response.use(
  response => response,
  error => {
    const message: string =
      error.response?.data?.errors?.[0]?.message ??
      error.response?.data?.message ??
      error.message ??
      'An unexpected error occurred'
    return Promise.reject(new Error(message))
  },
)
