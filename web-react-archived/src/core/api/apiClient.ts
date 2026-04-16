// web-react/src/core/api/apiClient.ts
import axios from 'axios'

function getKeycloakToken(): string | null {
  if (typeof window === 'undefined') return null
  return (window as Window & { __keycloakToken?: string }).__keycloakToken ?? null
}

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

// Auth header interceptor
// Dev bypass: attaches a static dev token.
// Production: replace with token from Keycloak adapter at cutover.
apiClient.interceptors.request.use(config => {
  const isBypass = import.meta.env.VITE_AUTH_BYPASS === 'true'
  const token = isBypass
    ? 'dev-bypass-token'
    : getKeycloakToken()

  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// Response error normaliser
// Extracts a human-readable message from the CBA API error envelope.
apiClient.interceptors.response.use(
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
