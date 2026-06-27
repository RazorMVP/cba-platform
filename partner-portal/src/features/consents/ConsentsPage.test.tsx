import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import ConsentsPage from './ConsentsPage'
import { apiClient } from '../../app/api/apiClient'

vi.mock('../../app/api/apiClient', () => ({ apiClient: { get: vi.fn(), delete: vi.fn() } }))
const get = apiClient.get as unknown as ReturnType<typeof vi.fn>
const del = apiClient.delete as unknown as ReturnType<typeof vi.fn>

const { mockUser } = vi.hoisted(() => ({ mockUser: { value: {} as Record<string, unknown> } }))
vi.mock('../../app/context/AuthContext', () => ({ useAuth: () => ({ user: mockUser.value }) }))

const AISP = { id: 'c1', consentId: 'urn:aisp:1', status: 'AUTHORISED', scopes: ['accounts:read'], expiryDate: '2027-01-01', createdAt: '2026-01-01' }
const PISP = { id: 'c2', consentId: 'urn:pisp:2', status: 'AWAITING_AUTHORISATION', scopes: ['payments:write'], expiryDate: '2027-01-01', createdAt: '2026-01-01' }

function renderPage(children: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ConsentsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUser.value = { organizationId: 'o1' }
    vi.stubGlobal('confirm', vi.fn(() => true))
  })

  it('lists consents and classifies scopes into AISP/PISP types', async () => {
    get.mockResolvedValue({ data: { data: [AISP, PISP] } })
    renderPage(<ConsentsPage />)

    expect(await screen.findByText('urn:aisp:1')).toBeInTheDocument()
    // "AISP"/"PISP" appear as both a filter tab and a type badge → expect ≥ 2 each.
    expect(screen.getAllByText('AISP').length).toBeGreaterThanOrEqual(2) // accounts:read → AISP badge
    expect(screen.getAllByText('PISP').length).toBeGreaterThanOrEqual(2) // payments:write → PISP badge
    expect(get).toHaveBeenCalledWith('/partners/o1/consents')
  })

  it('filters by type when a type tab is selected', async () => {
    get.mockResolvedValue({ data: { data: [AISP, PISP] } })
    renderPage(<ConsentsPage />)
    await screen.findByText('urn:aisp:1')

    await userEvent.click(screen.getByRole('button', { name: 'PISP' }))
    expect(screen.queryByText('urn:aisp:1')).not.toBeInTheDocument()
    expect(screen.getByText('urn:pisp:2')).toBeInTheDocument()
  })

  it('shows the empty-state when no consent matches the filters', async () => {
    get.mockResolvedValue({ data: { data: [AISP] } })
    renderPage(<ConsentsPage />)
    await screen.findByText('urn:aisp:1')

    await userEvent.click(screen.getByRole('button', { name: 'PISP' }))
    expect(screen.getByText(/no consents match/i)).toBeInTheDocument()
  })

  it('revokes only AUTHORISED consents and calls the delete endpoint', async () => {
    get.mockResolvedValue({ data: { data: [AISP, PISP] } })
    del.mockResolvedValue({ data: {} })
    renderPage(<ConsentsPage />)
    await screen.findByText('urn:aisp:1')

    // PISP (awaiting) card has no revoke button; AISP (authorised) does.
    const pispCard = screen.getByText('urn:pisp:2').closest('div.bg-white')!
    expect(within(pispCard).queryByRole('button', { name: /revoke/i })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /revoke/i }))
    await waitFor(() => expect(del).toHaveBeenCalledWith('/partners/o1/consents/c1'))
  })

  it('does not revoke when confirmation is declined', async () => {
    vi.stubGlobal('confirm', vi.fn(() => false))
    get.mockResolvedValue({ data: { data: [AISP] } })
    renderPage(<ConsentsPage />)
    await screen.findByText('urn:aisp:1')

    await userEvent.click(screen.getByRole('button', { name: /revoke/i }))
    expect(del).not.toHaveBeenCalled()
  })
})
