import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import DashboardPage from './DashboardPage'
import { apiClient } from '../../app/api/apiClient'

vi.mock('../../app/api/apiClient', () => ({ apiClient: { get: vi.fn() } }))
const get = apiClient.get as unknown as ReturnType<typeof vi.fn>

const { mockUser } = vi.hoisted(() => ({ mockUser: { value: {} as Record<string, unknown> } }))
vi.mock('../../app/context/AuthContext', () => ({ useAuth: () => ({ user: mockUser.value }) }))

const USAGE = {
  totalRequests: 1000, successRequests: 950, failedRequests: 50,
  webhookDeliveryRate: 99, dailyCalls: [],
  topEndpoints: [{ path: '/accounts', method: 'GET', count: 600 }],
}

function renderPage(children: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUser.value = { organizationId: 'o1', organizationName: 'Acme', environment: 'SANDBOX' }
  })

  it('renders KPIs from the usage query and computes the success rate', async () => {
    get.mockResolvedValue({ data: { data: USAGE } })
    renderPage(<DashboardPage />)

    expect(screen.getByText('Acme')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('1,000')).toBeInTheDocument()) // total calls
    expect(screen.getByText('95%')).toBeInTheDocument() // 950/1000 success rate
    expect(get).toHaveBeenCalledWith('/partners/o1/usage')
  })

  it('shows the Sandbox banner only for sandbox environments', async () => {
    get.mockResolvedValue({ data: { data: USAGE } })
    renderPage(<DashboardPage />)
    expect(screen.getByText(/sandbox mode/i)).toBeInTheDocument()
  })

  it('hides the Sandbox banner for production', async () => {
    mockUser.value = { organizationId: 'o1', organizationName: 'Acme', environment: 'PRODUCTION' }
    get.mockResolvedValue({ data: { data: { ...USAGE, topEndpoints: [] } } })
    renderPage(<DashboardPage />)
    expect(screen.queryByText(/sandbox mode/i)).not.toBeInTheDocument()
  })

  it('renders the empty-state when there are no top endpoints', async () => {
    get.mockResolvedValue({ data: { data: { ...USAGE, topEndpoints: [] } } })
    renderPage(<DashboardPage />)
    expect(await screen.findByText(/no api calls recorded yet/i)).toBeInTheDocument()
  })
})
