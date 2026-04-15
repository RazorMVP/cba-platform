// web-react/src/core/auth/AuthContext.test.tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AuthProvider, useAuth } from './AuthContext'

function RoleDisplay() {
  const { roles, isAdmin, isTeller } = useAuth()
  return (
    <div>
      <span data-testid="roles">{roles.join(',')}</span>
      <span data-testid="isAdmin">{String(isAdmin)}</span>
      <span data-testid="isTeller">{String(isTeller)}</span>
    </div>
  )
}

describe('AuthContext — bypass mode', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AUTH_BYPASS', 'true')
  })

  it('injects ADMIN, TELLER, CUSTOMER roles when bypass is true', () => {
    render(<AuthProvider><RoleDisplay /></AuthProvider>)
    expect(screen.getByTestId('roles').textContent).toBe('ADMIN,TELLER,CUSTOMER')
    expect(screen.getByTestId('isAdmin').textContent).toBe('true')
    expect(screen.getByTestId('isTeller').textContent).toBe('true')
  })
})

describe('AuthContext — production mode', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AUTH_BYPASS', 'false')
  })

  it('has no roles when bypass is false and no token provided', () => {
    render(<AuthProvider><RoleDisplay /></AuthProvider>)
    expect(screen.getByTestId('roles').textContent).toBe('')
    expect(screen.getByTestId('isAdmin').textContent).toBe('false')
  })
})
