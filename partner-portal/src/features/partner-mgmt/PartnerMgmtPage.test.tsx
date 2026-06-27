import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import PartnerMgmtPage from './PartnerMgmtPage'
import { apiClient } from '../../app/api/apiClient'

vi.mock('../../app/api/apiClient', () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }))
const get = apiClient.get as unknown as ReturnType<typeof vi.fn>
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>

const PENDING = { id: 'p1', organizationName: 'Acme Fintech', email: 'a@acme.io', status: 'PENDING_REVIEW', tier: 'BASIC', createdAt: '2026-01-01', totalApiCalls: 1200 }
const LIVE = { id: 'p2', organizationName: 'Globex', email: 'b@globex.io', status: 'PRODUCTION', tier: 'PRO', createdAt: '2026-01-01', totalApiCalls: 99999 }

function renderPage(children: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('PartnerMgmtPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lists partners and computes the summary counts', async () => {
    get.mockResolvedValue({ data: { data: [PENDING, LIVE] } })
    renderPage(<PartnerMgmtPage />)

    expect(await screen.findByText('Acme Fintech')).toBeInTheDocument()
    expect(screen.getByText('Globex')).toBeInTheDocument()
    expect(screen.getByText('99,999')).toBeInTheDocument() // toLocaleString
    expect(get).toHaveBeenCalledWith('/partners')
  })

  it('filters by search query across name and email', async () => {
    get.mockResolvedValue({ data: { data: [PENDING, LIVE] } })
    renderPage(<PartnerMgmtPage />)
    await screen.findByText('Acme Fintech')

    await userEvent.type(screen.getByPlaceholderText(/search partners/i), 'globex')
    expect(screen.queryByText('Acme Fintech')).not.toBeInTheDocument()
    expect(screen.getByText('Globex')).toBeInTheDocument()
  })

  it('selecting a pending partner reveals approve/reject; live partner does not', async () => {
    get.mockResolvedValue({ data: { data: [PENDING, LIVE] } })
    renderPage(<PartnerMgmtPage />)
    await screen.findByText('Acme Fintech')

    await userEvent.click(screen.getByText('Globex'))
    expect(screen.queryByRole('button', { name: /approve/i })).not.toBeInTheDocument()

    await userEvent.click(screen.getByText('Acme Fintech'))
    expect(screen.getByRole('button', { name: /approve/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /reject/i })).toBeInTheDocument()
  })

  it('approves a pending partner and clears the selection', async () => {
    get.mockResolvedValue({ data: { data: [PENDING] } })
    post.mockResolvedValue({ data: {} })
    renderPage(<PartnerMgmtPage />)
    await screen.findByText('Acme Fintech')

    await userEvent.click(screen.getByText('Acme Fintech'))
    await userEvent.click(screen.getByRole('button', { name: /approve/i }))

    await waitFor(() => expect(post).toHaveBeenCalledWith('/partners/p1/approve'))
    expect(await screen.findByText(/select a partner to view details/i)).toBeInTheDocument()
  })

  it('rejects a pending partner', async () => {
    get.mockResolvedValue({ data: { data: [PENDING] } })
    post.mockResolvedValue({ data: {} })
    renderPage(<PartnerMgmtPage />)
    await screen.findByText('Acme Fintech')

    await userEvent.click(screen.getByText('Acme Fintech'))
    await userEvent.click(screen.getByRole('button', { name: /reject/i }))

    await waitFor(() => expect(post).toHaveBeenCalledWith('/partners/p1/reject'))
  })

  it('shows the empty-state when no partner matches the filters', async () => {
    get.mockResolvedValue({ data: { data: [PENDING] } })
    renderPage(<PartnerMgmtPage />)
    await screen.findByText('Acme Fintech')

    await userEvent.type(screen.getByPlaceholderText(/search partners/i), 'nomatch')
    expect(screen.getByText(/no partners match/i)).toBeInTheDocument()
  })
})
