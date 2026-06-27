import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import ApplyPage from './ApplyPage'
import { apiClient } from '../../app/api/apiClient'

vi.mock('../../app/api/apiClient', () => ({ apiClient: { post: vi.fn() } }))
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

async function fillRequired() {
  // Labels aren't associated to controls, so target by role/option text + placeholder.
  const selects = screen.getAllByRole('combobox')
  await userEvent.selectOptions(selects[0], 'Neobank')            // business type
  await userEvent.type(screen.getByPlaceholderText(/what will you build/i), 'Account aggregation')
  await userEvent.selectOptions(selects[1], '1M+')               // estimated monthly calls
}

describe('ApplyPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUser.value = { organizationId: 'o1', environment: 'SANDBOX' }
  })

  it('shows the "You\'re Live" state for production accounts and does not render the form', () => {
    mockUser.value = { organizationId: 'o1', environment: 'PRODUCTION' }
    renderPage(<ApplyPage />)
    expect(screen.getByText(/you're live/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /submit application/i })).not.toBeInTheDocument()
  })

  it('renders the application form for sandbox accounts', () => {
    renderPage(<ApplyPage />)
    expect(screen.getByRole('button', { name: /submit application/i })).toBeInTheDocument()
  })

  it('keeps submit disabled until required fields are filled', async () => {
    renderPage(<ApplyPage />)
    expect(screen.getByRole('button', { name: /submit application/i })).toBeDisabled()
    await fillRequired()
    expect(screen.getByRole('button', { name: /submit application/i })).toBeEnabled()
  })

  it('posts the application to the org endpoint and shows the submitted state', async () => {
    post.mockResolvedValue({ data: {} })
    renderPage(<ApplyPage />)

    await fillRequired()
    await userEvent.click(screen.getByRole('button', { name: /submit application/i }))

    await waitFor(() => expect(post).toHaveBeenCalledWith(
      '/partners/o1/applications',
      expect.objectContaining({ businessType: 'Neobank', useCase: 'Account aggregation', estimatedMonthlyCalls: '1M+' }),
    ))
    expect(await screen.findByText(/application submitted/i)).toBeInTheDocument()
  })

  it('shows an inline error and stays on the form when the submission fails', async () => {
    post.mockRejectedValue(new Error('500'))
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    renderPage(<ApplyPage />)

    await fillRequired()
    await userEvent.click(screen.getByRole('button', { name: /submit application/i }))

    expect(await screen.findByText(/submission failed/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /submit application/i })).toBeInTheDocument()
    spy.mockRestore()
  })
})
