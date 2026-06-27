import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AppShell } from './AppShell'

const { mockUser, mockLogout } = vi.hoisted(() => ({
  mockUser: { value: {} as Record<string, unknown> },
  mockLogout: vi.fn(),
}))
vi.mock('../../app/context/AuthContext', () => ({
  useAuth: () => ({ user: mockUser.value, logout: mockLogout }),
}))

const DEVELOPER = { email: 'dev@x.com', organizationName: 'Acme', role: 'DEVELOPER', environment: 'SANDBOX' }
const ADMIN = { email: 'admin@x.com', organizationName: 'NubBank', role: 'ADMIN', environment: 'PRODUCTION' }

function renderShell() {
  return render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<div>dashboard outlet</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('AppShell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUser.value = { ...DEVELOPER }
  })

  it('renders the org name, email and the routed outlet', () => {
    renderShell()
    expect(screen.getByText('Acme')).toBeInTheDocument()
    expect(screen.getByText('dev@x.com')).toBeInTheDocument()
    expect(screen.getByText('dashboard outlet')).toBeInTheDocument()
  })

  it('renders the common nav and the environment badge', () => {
    renderShell()
    expect(screen.getByRole('link', { name: /dashboard/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /api keys/i })).toBeInTheDocument()
    expect(screen.getByText('SANDBOX')).toBeInTheDocument()
  })

  it('hides admin-only nav for a DEVELOPER', () => {
    renderShell()
    expect(screen.queryByRole('link', { name: /partner management/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /usage analytics/i })).not.toBeInTheDocument()
  })

  it('shows admin-only nav for an ADMIN', () => {
    mockUser.value = { ...ADMIN }
    renderShell()
    expect(screen.getByRole('link', { name: /partner management/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /usage analytics/i })).toBeInTheDocument()
    expect(screen.getByText('PRODUCTION')).toBeInTheDocument()
  })

  it('calls logout when the sign-out button is clicked', async () => {
    renderShell()
    await userEvent.click(screen.getByRole('button', { name: /sign out/i }))
    expect(mockLogout).toHaveBeenCalledTimes(1)
  })
})
