import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthProvider, useAuth } from './AuthContext'
import { apiClient } from '../api/apiClient'

// Mock the api client so we don't pull in the real axios instance + router chain.
vi.mock('../api/apiClient', () => ({ apiClient: { post: vi.fn() } }))
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>

const b64 = (o: unknown) => btoa(JSON.stringify(o))
const tokenWithExp = (epochSeconds: number) => `h.${b64({ exp: epochSeconds })}.s`
const futureToken = () => tokenWithExp(Math.floor(Date.now() / 1000) + 3600)
const expiredToken = () => tokenWithExp(Math.floor(Date.now() / 1000) - 3600)

const USER = {
  id: 'u1', email: 'dev@x.com', role: 'DEVELOPER', organizationId: 'o1',
  organizationName: 'Org', status: 'SANDBOX', tier: 'BASIC', environment: 'SANDBOX',
}

function Consumer() {
  const { user, login, logout, isAuthenticated } = useAuth()
  return (
    <div>
      <span data-testid="auth">{String(isAuthenticated)}</span>
      <span data-testid="email">{user?.email ?? '-'}</span>
      <button onClick={() => login('dev@x.com', 'pw')}>login</button>
      <button onClick={logout}>logout</button>
    </div>
  )
}

describe('AuthContext', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('starts unauthenticated when no token is stored', () => {
    render(<AuthProvider><Consumer /></AuthProvider>)
    expect(screen.getByTestId('auth')).toHaveTextContent('false')
  })

  it('hydrates the user from a valid (non-expired) stored token', () => {
    localStorage.setItem('partner_token', futureToken())
    localStorage.setItem('partner_user', JSON.stringify(USER))
    render(<AuthProvider><Consumer /></AuthProvider>)
    expect(screen.getByTestId('auth')).toHaveTextContent('true')
    expect(screen.getByTestId('email')).toHaveTextContent('dev@x.com')
  })

  it('ignores and clears an expired stored token', () => {
    localStorage.setItem('partner_token', expiredToken())
    localStorage.setItem('partner_user', JSON.stringify(USER))
    render(<AuthProvider><Consumer /></AuthProvider>)
    expect(screen.getByTestId('auth')).toHaveTextContent('false')
    expect(localStorage.getItem('partner_token')).toBeNull()
    expect(localStorage.getItem('partner_user')).toBeNull()
  })

  it('ignores a malformed token (atob/JSON.parse throws → treated as expired)', () => {
    localStorage.setItem('partner_token', 'not-a-jwt')
    localStorage.setItem('partner_user', JSON.stringify(USER))
    render(<AuthProvider><Consumer /></AuthProvider>)
    expect(screen.getByTestId('auth')).toHaveTextContent('false')
  })

  it('login posts credentials, stores token+user, and authenticates', async () => {
    post.mockResolvedValue({ data: { data: { token: futureToken(), user: USER } } })
    render(<AuthProvider><Consumer /></AuthProvider>)

    await userEvent.click(screen.getByText('login'))

    await waitFor(() => expect(screen.getByTestId('auth')).toHaveTextContent('true'))
    expect(post).toHaveBeenCalledWith('/partners/auth/login', { email: 'dev@x.com', password: 'pw' })
    expect(localStorage.getItem('partner_token')).toBeTruthy()
    expect(JSON.parse(localStorage.getItem('partner_user')!).email).toBe('dev@x.com')
    expect(screen.getByTestId('email')).toHaveTextContent('dev@x.com')
  })

  it('logout clears storage and resets state', async () => {
    localStorage.setItem('partner_token', futureToken())
    localStorage.setItem('partner_user', JSON.stringify(USER))
    render(<AuthProvider><Consumer /></AuthProvider>)
    expect(screen.getByTestId('auth')).toHaveTextContent('true')

    await userEvent.click(screen.getByText('logout'))

    expect(screen.getByTestId('auth')).toHaveTextContent('false')
    expect(localStorage.getItem('partner_token')).toBeNull()
    expect(localStorage.getItem('partner_user')).toBeNull()
  })

  it('useAuth throws when used outside an AuthProvider', () => {
    const Bare = () => {
      useAuth()
      return null
    }
    // Silence the expected React error boundary console noise.
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => render(<Bare />)).toThrow('useAuth must be used within AuthProvider')
    spy.mockRestore()
  })
})
