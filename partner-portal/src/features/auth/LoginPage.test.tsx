import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import LoginPage from './LoginPage'

const { mockLogin, mockNavigate } = vi.hoisted(() => ({
  mockLogin: vi.fn(),
  mockNavigate: vi.fn(),
}))

vi.mock('../../app/context/AuthContext', () => ({ useAuth: () => ({ login: mockLogin }) }))
vi.mock('react-router-dom', async (orig) => ({
  ...(await orig<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
}))

function renderPage() {
  return render(<MemoryRouter><LoginPage /></MemoryRouter>)
}

describe('LoginPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders the sign-in form', () => {
    renderPage()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
    expect(screen.getByPlaceholderText('dev@yourcompany.com')).toBeInTheDocument()
  })

  it('submits credentials and navigates to the dashboard on success', async () => {
    mockLogin.mockResolvedValue(undefined)
    renderPage()

    await userEvent.type(screen.getByPlaceholderText('dev@yourcompany.com'), 'dev@x.com')
    await userEvent.type(screen.getByPlaceholderText('••••••••'), 'secret')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => expect(mockLogin).toHaveBeenCalledWith('dev@x.com', 'secret'))
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard')
  })

  it('shows an error and does not navigate when login fails', async () => {
    mockLogin.mockRejectedValue(new Error('401'))
    renderPage()

    await userEvent.type(screen.getByPlaceholderText('dev@yourcompany.com'), 'dev@x.com')
    await userEvent.type(screen.getByPlaceholderText('••••••••'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByText(/invalid credentials/i)).toBeInTheDocument()
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})
