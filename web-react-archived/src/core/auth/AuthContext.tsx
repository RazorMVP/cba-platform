// web-react/src/core/auth/AuthContext.tsx
import { createContext, useContext, useMemo, type ReactNode } from 'react'

export type Role = 'ADMIN' | 'TELLER' | 'CUSTOMER'

interface AuthState {
  roles: Role[]
  isAdmin: boolean
  isTeller: boolean
  isCustomer: boolean
  token: string | null
}

const AuthContext = createContext<AuthState | null>(null)

const BYPASS_ROLES: Role[] = ['ADMIN', 'TELLER', 'CUSTOMER']

export function AuthProvider({ children }: { children: ReactNode }) {
  const isBypass = import.meta.env.VITE_AUTH_BYPASS === 'true'

  const value = useMemo<AuthState>(() => {
    if (isBypass) {
      return {
        roles: BYPASS_ROLES,
        isAdmin: true,
        isTeller: true,
        isCustomer: true,
        token: 'dev-bypass-token',
      }
    }
    // Production: token would be read from Keycloak session
    // Placeholder until Keycloak is wired at cutover
    return {
      roles: [],
      isAdmin: false,
      isTeller: false,
      isCustomer: false,
      token: null,
    }
  }, [isBypass])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
