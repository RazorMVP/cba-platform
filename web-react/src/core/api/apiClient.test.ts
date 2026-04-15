// web-react/src/core/api/apiClient.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'

describe('apiClient', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_API_URL', 'http://test-api.local/api/v1')
    vi.stubEnv('VITE_AUTH_BYPASS', 'true')
  })

  it('has the correct base URL', async () => {
    const { apiClient } = await import('./apiClient')
    expect(apiClient.defaults.baseURL).toBe('http://test-api.local/api/v1')
  })

  it('attaches Authorization header in bypass mode', async () => {
    const { apiClient } = await import('./apiClient')

    // Use a custom adapter to capture the fully-resolved config after all
    // request interceptors have run. Axios processes request interceptors LIFO,
    // so a second-registered interceptor would fire before the auth interceptor.
    // The adapter receives the final merged config.
    let capturedAuth: unknown
    const captureAdapter = (config: { headers: Record<string, unknown> }) => {
      capturedAuth = config.headers['Authorization']
      // Return a settled promise that looks like an axios response
      return Promise.resolve({
        data: {},
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      })
    }

    await apiClient.get('/test', { adapter: captureAdapter as never })
    expect(capturedAuth).toBe('Bearer dev-bypass-token')
  })
})
