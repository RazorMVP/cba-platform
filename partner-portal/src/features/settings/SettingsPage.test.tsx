import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import SettingsPage from './SettingsPage'
import { apiClient } from '../../app/api/apiClient'

vi.mock('../../app/api/apiClient', () => ({ apiClient: { put: vi.fn(), post: vi.fn() } }))
const put = apiClient.put as unknown as ReturnType<typeof vi.fn>
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>

const { mockUser } = vi.hoisted(() => ({ mockUser: { value: {} as Record<string, unknown> } }))
vi.mock('../../app/context/AuthContext', () => ({ useAuth: () => ({ user: mockUser.value }) }))

function renderPage(children: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('SettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUser.value = {
      id: 'u1', email: 'dev@x.com', role: 'DEVELOPER',
      organizationId: 'o1', organizationName: 'Acme', environment: 'SANDBOX', tier: 'BASIC',
    }
  })

  it('defaults to the profile tab pre-filled from the auth user', () => {
    renderPage(<SettingsPage />)
    expect(screen.getByDisplayValue('dev@x.com')).toBeInTheDocument()
    expect(screen.getByDisplayValue('DEVELOPER')).toBeInTheDocument() // read-only role
  })

  it('saves the profile via PUT and shows the saved banner', async () => {
    put.mockResolvedValue({ data: {} })
    renderPage(<SettingsPage />)

    const email = screen.getByDisplayValue('dev@x.com')
    await userEvent.clear(email)
    await userEvent.type(email, 'new@x.com')
    await userEvent.click(screen.getByRole('button', { name: /save profile/i }))

    await waitFor(() => expect(put).toHaveBeenCalledWith('/partners/users/u1', { email: 'new@x.com' }))
    expect(await screen.findByText(/changes saved successfully/i)).toBeInTheDocument()
  })

  it('saves the organization via PUT on the org tab', async () => {
    put.mockResolvedValue({ data: {} })
    renderPage(<SettingsPage />)

    await userEvent.click(screen.getByRole('button', { name: /^organization$/i }))
    await userEvent.click(screen.getByRole('button', { name: /save organization/i }))

    await waitFor(() => expect(put).toHaveBeenCalledWith(
      '/partners/o1',
      expect.objectContaining({ organizationName: 'Acme' }),
    ))
  })

  it('keeps Change Password disabled until the new passwords match', async () => {
    renderPage(<SettingsPage />)
    await userEvent.click(screen.getByRole('button', { name: /^security$/i }))

    const btn = screen.getByRole('button', { name: /change password/i })
    expect(btn).toBeDisabled()

    const inputs = screen.getAllByDisplayValue('') // 3 empty password fields
    await userEvent.type(inputs[0], 'oldpw')
    await userEvent.type(inputs[1], 'newpw1')
    await userEvent.type(inputs[2], 'different')
    expect(btn).toBeDisabled() // mismatch

    await userEvent.clear(inputs[2])
    await userEvent.type(inputs[2], 'newpw1')
    expect(btn).toBeEnabled()
  })

  it('changes the password via POST with current+new payload', async () => {
    post.mockResolvedValue({ data: {} })
    renderPage(<SettingsPage />)
    await userEvent.click(screen.getByRole('button', { name: /^security$/i }))

    const inputs = screen.getAllByDisplayValue('')
    await userEvent.type(inputs[0], 'oldpw')
    await userEvent.type(inputs[1], 'newpw1')
    await userEvent.type(inputs[2], 'newpw1')
    await userEvent.click(screen.getByRole('button', { name: /change password/i }))

    await waitFor(() => expect(post).toHaveBeenCalledWith(
      '/partners/users/u1/change-password',
      { currentPassword: 'oldpw', newPassword: 'newpw1' },
    ))
  })

  it('shows an error when saving the profile fails', async () => {
    put.mockRejectedValue(new Error('500'))
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    renderPage(<SettingsPage />)

    await userEvent.click(screen.getByRole('button', { name: /save profile/i }))
    expect(await screen.findByText(/save failed/i)).toBeInTheDocument()
    spy.mockRestore()
  })
})
