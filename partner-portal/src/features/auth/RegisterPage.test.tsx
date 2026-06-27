import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import RegisterPage from './RegisterPage'
import { apiClient } from '../../app/api/apiClient'

vi.mock('../../app/api/apiClient', () => ({ apiClient: { post: vi.fn() } }))
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }))
vi.mock('react-router-dom', async (orig) => ({
  ...(await orig<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
}))

function renderPage() {
  return render(<MemoryRouter><RegisterPage /></MemoryRouter>)
}

async function fill(org: string, email: string, pw: string, confirm: string) {
  await userEvent.type(screen.getByPlaceholderText('Acme Fintech Ltd'), org)
  await userEvent.type(screen.getByPlaceholderText('dev@acme.io'), email)
  await userEvent.type(screen.getByPlaceholderText('8+ characters'), pw)
  await userEvent.type(screen.getByPlaceholderText('Repeat password'), confirm)
}

describe('RegisterPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders the registration form', () => {
    renderPage()
    expect(screen.getByRole('button', { name: /create free account/i })).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Acme Fintech Ltd')).toBeInTheDocument()
  })

  it('posts only org/email/password (omits the confirm field) and shows the success screen', async () => {
    post.mockResolvedValue({ data: {} })
    renderPage()

    await fill('Acme', 'dev@acme.io', 'password1', 'password1')
    await userEvent.click(screen.getByRole('button', { name: /create free account/i }))

    await waitFor(() => expect(post).toHaveBeenCalledWith('/partners/register', {
      organizationName: 'Acme', email: 'dev@acme.io', password: 'password1',
    }))
    expect(await screen.findByText(/account created/i)).toBeInTheDocument()
  })

  it('blocks submit and shows a mismatch error when passwords differ', async () => {
    renderPage()

    await fill('Acme', 'dev@acme.io', 'password1', 'password2')
    await userEvent.click(screen.getByRole('button', { name: /create free account/i }))

    expect(await screen.findByText(/passwords do not match/i)).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  it('surfaces the server error message on a failed registration', async () => {
    post.mockRejectedValue({ response: { data: { errors: [{ message: 'Email already registered' }] } } })
    renderPage()

    await fill('Acme', 'dev@acme.io', 'password1', 'password1')
    await userEvent.click(screen.getByRole('button', { name: /create free account/i }))

    expect(await screen.findByText('Email already registered')).toBeInTheDocument()
  })

  it('falls back to a generic error when the server gives no message', async () => {
    post.mockRejectedValue(new Error('network'))
    renderPage()

    await fill('Acme', 'dev@acme.io', 'password1', 'password1')
    await userEvent.click(screen.getByRole('button', { name: /create free account/i }))

    expect(await screen.findByText(/registration failed/i)).toBeInTheDocument()
  })

  it('navigates to /login from the success screen', async () => {
    post.mockResolvedValue({ data: {} })
    renderPage()

    await fill('Acme', 'dev@acme.io', 'password1', 'password1')
    await userEvent.click(screen.getByRole('button', { name: /create free account/i }))

    const signIn = await screen.findByRole('button', { name: /sign in now/i })
    await userEvent.click(signIn)
    expect(mockNavigate).toHaveBeenCalledWith('/login')
  })
})
