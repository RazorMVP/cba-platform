import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import WebhooksPage from './WebhooksPage'
import { apiClient } from '../../app/api/apiClient'

vi.mock('../../app/api/apiClient', () => ({ apiClient: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))
const get = apiClient.get as unknown as ReturnType<typeof vi.fn>
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>
const del = apiClient.delete as unknown as ReturnType<typeof vi.fn>

const { mockUser } = vi.hoisted(() => ({ mockUser: { value: {} as Record<string, unknown> } }))
vi.mock('../../app/context/AuthContext', () => ({ useAuth: () => ({ user: mockUser.value }) }))

const HOOK = { id: 'w1', name: 'My Hook', callbackUrl: 'https://app.io/hook', events: ['PAYMENT.COMPLETED', 'CONSENT.CREATED', 'API_KEY.CREATED'], active: true, createdAt: '2026-01-01' }
const DELIVERY = { id: 'd1', deliveryUuid: 'uuid-1', eventType: 'PAYMENT.COMPLETED', httpStatus: 200, status: 'DELIVERED' as const, attemptCount: 1, lastAttemptAt: '2026-01-02T10:00:00Z' }

// Route get() by URL so the webhooks list and the deliveries query are both served.
function routeGet(hooks: unknown[], deliveries: unknown[] = []) {
  get.mockImplementation((url: string) =>
    url.includes('/deliveries')
      ? Promise.resolve({ data: { data: deliveries } })
      : Promise.resolve({ data: { data: hooks } }),
  )
}

function renderPage(children: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('WebhooksPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUser.value = { organizationId: 'o1' }
    vi.stubGlobal('confirm', vi.fn(() => true))
  })

  it('lists webhooks and truncates the event chips with a +N overflow', async () => {
    routeGet([HOOK])
    renderPage(<WebhooksPage />)

    expect(await screen.findByText('My Hook')).toBeInTheDocument()
    expect(screen.getByText('https://app.io/hook')).toBeInTheDocument()
    expect(screen.getByText('+1')).toBeInTheDocument() // 3 events, only 2 shown
    expect(get).toHaveBeenCalledWith('/partners/o1/webhooks')
  })

  it('renders the empty-state when no webhooks are registered', async () => {
    routeGet([])
    renderPage(<WebhooksPage />)
    expect(await screen.findByText(/no webhooks registered yet/i)).toBeInTheDocument()
  })

  it('loads the delivery log when a webhook is selected', async () => {
    routeGet([HOOK], [DELIVERY])
    renderPage(<WebhooksPage />)
    await screen.findByText('My Hook')

    await userEvent.click(screen.getByText('My Hook'))
    expect(await screen.findByText(/delivery log/i)).toBeInTheDocument()
    expect(screen.getByText('DELIVERED')).toBeInTheDocument()
    await waitFor(() => expect(get).toHaveBeenCalledWith('/partners/o1/webhooks/w1/deliveries'))
  })

  it('registers a webhook from the modal with name, url and selected events', async () => {
    routeGet([])
    post.mockResolvedValue({ data: {} })
    renderPage(<WebhooksPage />)
    await screen.findByText(/no webhooks registered yet/i)

    await userEvent.click(screen.getByRole('button', { name: /register webhook/i }))
    await userEvent.type(screen.getByPlaceholderText('My Webhook'), 'Hook A')
    await userEvent.type(screen.getByPlaceholderText('https://yourapp.com/webhook'), 'https://x.io/cb')
    await userEvent.click(screen.getByLabelText('PAYMENT.COMPLETED'))
    // dialog "Register" submit button (not the header toolbar one)
    const submit = screen.getAllByRole('button', { name: /^register$/i }).at(-1)!
    await userEvent.click(submit)

    await waitFor(() => expect(post).toHaveBeenCalledWith(
      '/partners/o1/webhooks',
      { name: 'Hook A', callbackUrl: 'https://x.io/cb', secret: '', events: ['PAYMENT.COMPLETED'] },
    ))
  })

  it('deletes a webhook after confirmation', async () => {
    routeGet([HOOK])
    del.mockResolvedValue({ data: {} })
    renderPage(<WebhooksPage />)
    await screen.findByText('My Hook')

    const card = screen.getByText('My Hook').closest('div.bg-white')!
    // first button in the card actions is the trash/delete
    await userEvent.click(within(card).getAllByRole('button')[0])
    await waitFor(() => expect(del).toHaveBeenCalledWith('/partners/o1/webhooks/w1'))
  })

  it('shows an error in the modal when registration fails', async () => {
    routeGet([])
    post.mockRejectedValue(new Error('500'))
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    renderPage(<WebhooksPage />)
    await screen.findByText(/no webhooks registered yet/i)

    await userEvent.click(screen.getByRole('button', { name: /register webhook/i }))
    await userEvent.type(screen.getByPlaceholderText('My Webhook'), 'Hook A')
    await userEvent.type(screen.getByPlaceholderText('https://yourapp.com/webhook'), 'https://x.io/cb')
    await userEvent.click(screen.getByLabelText('PAYMENT.COMPLETED'))
    await userEvent.click(screen.getAllByRole('button', { name: /^register$/i }).at(-1)!)

    expect(await screen.findByText(/failed to register webhook/i)).toBeInTheDocument()
    spy.mockRestore()
  })
})
