import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import UsageAnalyticsPage from './UsageAnalyticsPage'
import { apiClient } from '../../app/api/apiClient'

vi.mock('../../app/api/apiClient', () => ({ apiClient: { get: vi.fn() } }))
const get = apiClient.get as unknown as ReturnType<typeof vi.fn>

const P1 = { organizationId: 'o1', organizationName: 'Acme', totalCalls: 1000, successCalls: 950, errorCalls: 50, topEndpoints: [{ path: '/accounts', count: 600 }], dailySeries: [] }
const P2 = { organizationId: 'o2', organizationName: 'Globex', totalCalls: 3000, successCalls: 3000, errorCalls: 0, topEndpoints: [], dailySeries: [] }

function renderPage(children: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('UsageAnalyticsPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('aggregates global KPIs across partners and queries the default 30-day window', async () => {
    get.mockResolvedValue({ data: { data: [P1, P2] } })
    renderPage(<UsageAnalyticsPage />)

    expect(await screen.findByText('4,000')).toBeInTheDocument()  // total calls 1000+3000
    // "Total Errors" KPI — scope to its card since "50" also appears in the per-partner row.
    const errorsKpi = screen.getByText('Total Errors').closest('div')!
    expect(within(errorsKpi).getByText('50')).toBeInTheDocument()
    // avg success rate = round((95 + 100) / 2) = 98%
    expect(screen.getByText('98%')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('/partners/usage?days=30')
  })

  it('sorts partners by total calls descending and falls back to — for no top endpoint', async () => {
    get.mockResolvedValue({ data: { data: [P1, P2] } })
    renderPage(<UsageAnalyticsPage />)
    await screen.findByText('Acme')

    const rows = screen.getAllByRole('row')
    // header is rows[0]; first data row should be Globex (3000 > 1000)
    expect(rows[1]).toHaveTextContent('Globex')
    expect(rows[2]).toHaveTextContent('Acme')
    expect(screen.getByText('—')).toBeInTheDocument() // Globex has no top endpoint
    expect(screen.getByText('/accounts')).toBeInTheDocument()
  })

  it('re-queries with the selected day window', async () => {
    get.mockResolvedValue({ data: { data: [] } })
    renderPage(<UsageAnalyticsPage />)
    await screen.findByText(/no usage data/i)

    await userEvent.selectOptions(screen.getByRole('combobox'), '7')
    await waitFor(() => expect(get).toHaveBeenCalledWith('/partners/usage?days=7'))
  })

  it('renders the empty-state when there is no usage data', async () => {
    get.mockResolvedValue({ data: { data: [] } })
    renderPage(<UsageAnalyticsPage />)
    expect(await screen.findByText(/no usage data for this period/i)).toBeInTheDocument()
  })

  it('reports a 0% success rate when there are no partners', async () => {
    get.mockResolvedValue({ data: { data: [] } })
    renderPage(<UsageAnalyticsPage />)
    await screen.findByText(/no usage data/i)
    expect(screen.getByText('0%')).toBeInTheDocument()
  })
})
