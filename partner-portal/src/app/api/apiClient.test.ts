import type { AxiosAdapter } from 'axios'

// Avoid pulling the real router (and the whole page tree) into the test.
vi.mock('../router', () => ({ router: { navigate: vi.fn() } }))

import { apiClient } from './apiClient'
import { router } from '../router'

const nav = router.navigate as unknown as ReturnType<typeof vi.fn>

describe('apiClient', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('uses the same-origin /api/v1 when VITE_API_URL is unset (backlog 19: the old fallback host had no DNS)', () => {
    expect(apiClient.defaults.baseURL).toBe('/api/v1')
  })

  it('VITE_API_URL overrides the base URL; an empty value falls back to /api/v1', async () => {
    try {
      vi.stubEnv('VITE_API_URL', 'https://api.example.test/api/v1')
      vi.resetModules()
      expect((await import('./apiClient')).apiClient.defaults.baseURL).toBe('https://api.example.test/api/v1')

      vi.stubEnv('VITE_API_URL', '')
      vi.resetModules()
      expect((await import('./apiClient')).apiClient.defaults.baseURL).toBe('/api/v1')
    } finally {
      vi.unstubAllEnvs()
      vi.resetModules()
    }
  })

  it('request interceptor attaches the Bearer token when present', async () => {
    const adapter = vi.fn(async (config) => ({
      data: { ok: true }, status: 200, statusText: 'OK', headers: {}, config,
    })) as unknown as AxiosAdapter
    apiClient.defaults.adapter = adapter
    localStorage.setItem('partner_token', 'tok123')

    await apiClient.get('/x')

    const sent = (adapter as unknown as ReturnType<typeof vi.fn>).mock.calls[0][0]
    expect(sent.headers.Authorization).toBe('Bearer tok123')
  })

  it('request interceptor omits Authorization when no token is stored', async () => {
    const adapter = vi.fn(async (config) => ({
      data: {}, status: 200, statusText: 'OK', headers: {}, config,
    })) as unknown as AxiosAdapter
    apiClient.defaults.adapter = adapter

    await apiClient.get('/x')

    const sent = (adapter as unknown as ReturnType<typeof vi.fn>).mock.calls[0][0]
    expect(sent.headers.Authorization).toBeUndefined()
  })

  it('response interceptor: 401 clears auth storage and redirects to /login', async () => {
    apiClient.defaults.adapter = (async () => {
      throw { response: { status: 401 } }
    }) as unknown as AxiosAdapter
    localStorage.setItem('partner_token', 't')
    localStorage.setItem('partner_user', 'u')

    await expect(apiClient.get('/x')).rejects.toBeDefined()

    expect(localStorage.getItem('partner_token')).toBeNull()
    expect(localStorage.getItem('partner_user')).toBeNull()
    expect(nav).toHaveBeenCalledWith('/login')
  })

  it('response interceptor: non-401 errors propagate without touching storage', async () => {
    apiClient.defaults.adapter = (async () => {
      throw { response: { status: 500 } }
    }) as unknown as AxiosAdapter
    localStorage.setItem('partner_token', 'keep')

    await expect(apiClient.get('/x')).rejects.toBeDefined()

    expect(localStorage.getItem('partner_token')).toBe('keep')
    expect(nav).not.toHaveBeenCalled()
  })
})
