import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { AuthGuard } from './AuthGuard'
import { StaffGuard } from './StaffGuard'
import { useAuth } from '../../app/context/AuthContext'

vi.mock('../../app/context/AuthContext', () => ({ useAuth: vi.fn() }))
const mockUseAuth = useAuth as unknown as ReturnType<typeof vi.fn>

function renderGuard(ui: ReactNode) {
  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route path="/protected" element={ui} />
        <Route path="/login" element={<div>login page</div>} />
        <Route path="/dashboard" element={<div>dashboard page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AuthGuard', () => {
  it('renders the protected children when authenticated', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true })
    renderGuard(<AuthGuard><div>secret</div></AuthGuard>)
    expect(screen.getByText('secret')).toBeInTheDocument()
  })

  it('redirects to /login when not authenticated', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false })
    renderGuard(<AuthGuard><div>secret</div></AuthGuard>)
    expect(screen.getByText('login page')).toBeInTheDocument()
    expect(screen.queryByText('secret')).not.toBeInTheDocument()
  })
})

describe('StaffGuard', () => {
  it('renders children for an ADMIN user', () => {
    mockUseAuth.mockReturnValue({ user: { role: 'ADMIN' } })
    renderGuard(<StaffGuard><div>admin area</div></StaffGuard>)
    expect(screen.getByText('admin area')).toBeInTheDocument()
  })

  it('redirects a DEVELOPER to /dashboard', () => {
    mockUseAuth.mockReturnValue({ user: { role: 'DEVELOPER' } })
    renderGuard(<StaffGuard><div>admin area</div></StaffGuard>)
    expect(screen.getByText('dashboard page')).toBeInTheDocument()
    expect(screen.queryByText('admin area')).not.toBeInTheDocument()
  })

  it('redirects when there is no user at all', () => {
    mockUseAuth.mockReturnValue({ user: null })
    renderGuard(<StaffGuard><div>admin area</div></StaffGuard>)
    expect(screen.getByText('dashboard page')).toBeInTheDocument()
  })
})
