import { createContext, useContext, useState, useCallback, type ReactNode } from 'react'
import { apiClient } from '../api/apiClient'

interface PartnerUser {
  id: string
  email: string
  role: 'DEVELOPER' | 'ADMIN'
  organizationId: string
  organizationName: string
  status: string
  tier: string
  environment: 'SANDBOX' | 'PRODUCTION'
}

interface AuthCtx {
  user: PartnerUser | null
  login: (email: string, password: string) => Promise<void>
  logout: () => void
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthCtx | null>(null)

function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return typeof payload.exp === 'number' && payload.exp * 1000 < Date.now()
  } catch {
    return true
  }
}

function loadStoredUser(): PartnerUser | null {
  try {
    const token = localStorage.getItem('partner_token')
    if (!token || isTokenExpired(token)) {
      localStorage.removeItem('partner_token')
      localStorage.removeItem('partner_user')
      return null
    }
    const raw = localStorage.getItem('partner_user')
    return raw ? (JSON.parse(raw) as PartnerUser) : null
  } catch {
    localStorage.removeItem('partner_token')
    localStorage.removeItem('partner_user')
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<PartnerUser | null>(loadStoredUser)

  const login = useCallback(async (email: string, password: string) => {
    const res = await apiClient.post<{ data: { token: string; user: PartnerUser } }>(
      '/partners/auth/login',
      { email, password }
    )
    localStorage.setItem('partner_token', res.data.data.token)
    localStorage.setItem('partner_user', JSON.stringify(res.data.data.user))
    setUser(res.data.data.user)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('partner_token')
    localStorage.removeItem('partner_user')
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, login, logout, isAuthenticated: !!user }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
