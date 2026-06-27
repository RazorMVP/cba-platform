import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import ApiKeysPage from './ApiKeysPage'
import { apiClient } from '../../app/api/apiClient'

vi.mock('../../app/api/apiClient', () => ({ apiClient: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))
const get = apiClient.get as unknown as ReturnType<typeof vi.fn>
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>
const del = apiClient.delete as unknown as ReturnType<typeof vi.fn>

const { mockUser } = vi.hoisted(() => ({ mockUser: { value: {} as Record<string, unknown> } }))
vi.mock('../../app/context/AuthContext', () => ({ useAuth: () => ({ user: mockUser.value }) }))

const KEY = {
  id: 'k1', name: 'Backend', keyPrefix: 'cba_live_ab', scopes: ['accounts:read'],
  tier: 'PRO', lastUsedAt: null, createdAt: '2026-01-01', active: true,
}

function renderPage(children: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ApiKeysPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUser.value = { organizationId: 'o1' }
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } })
    vi.stubGlobal('confirm', vi.fn(() => true))
  })

  it('lists keys from the query, masking the prefix and labelling the tier', async () => {
    get.mockResolvedValue({ data: { data: [KEY] } })
    renderPage(<ApiKeysPage />)

    expect(await screen.findByText('Backend')).toBeInTheDocument()
    expect(screen.getByText('cba_live_ab•••')).toBeInTheDocument()
    expect(screen.getByText('PRO')).toBeInTheDocument()
    expect(screen.getByText('Never')).toBeInTheDocument() // lastUsedAt null
    expect(get).toHaveBeenCalledWith('/partners/o1/api-keys')
  })

  it('renders the empty-state when there are no keys', async () => {
    get.mockResolvedValue({ data: { data: [] } })
    renderPage(<ApiKeysPage />)
    expect(await screen.findByText(/no api keys yet/i)).toBeInTheDocument()
  })

  it('issues a key from the modal and reveals it once', async () => {
    get.mockResolvedValue({ data: { data: [] } })
    post.mockResolvedValue({ data: { data: { key: 'cba_live_SECRETVALUE' } } })
    renderPage(<ApiKeysPage />)
    await screen.findByText(/no api keys yet/i)

    await userEvent.click(screen.getByRole('button', { name: /issue new key/i }))
    await userEvent.type(screen.getByPlaceholderText(/production backend/i), 'My Key')
    await userEvent.click(screen.getByRole('button', { name: /^issue key$/i }))

    await waitFor(() => expect(post).toHaveBeenCalledWith(
      '/partners/o1/api-keys',
      expect.objectContaining({ name: 'My Key', scopes: expect.arrayContaining(['accounts:read']) }),
    ))
    expect(await screen.findByText('cba_live_SECRETVALUE')).toBeInTheDocument()
    expect(screen.getByText(/copy it now/i)).toBeInTheDocument()
  })

  it('copies the revealed key to the clipboard', async () => {
    get.mockResolvedValue({ data: { data: [] } })
    post.mockResolvedValue({ data: { data: { key: 'cba_live_SECRET' } } })
    renderPage(<ApiKeysPage />)
    await screen.findByText(/no api keys yet/i)

    await userEvent.click(screen.getByRole('button', { name: /issue new key/i }))
    await userEvent.type(screen.getByPlaceholderText(/production backend/i), 'My Key')
    await userEvent.click(screen.getByRole('button', { name: /^issue key$/i }))
    await screen.findByText('cba_live_SECRET')

    const reveal = screen.getByText('cba_live_SECRET').closest('div')!
    await userEvent.click(within(reveal).getByRole('button'))
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('cba_live_SECRET')
  })

  it('revokes a key after confirmation', async () => {
    get.mockResolvedValue({ data: { data: [KEY] } })
    del.mockResolvedValue({ data: {} })
    renderPage(<ApiKeysPage />)
    await screen.findByText('Backend')

    const row = screen.getByText('Backend').closest('tr')!
    await userEvent.click(within(row).getByRole('button'))

    await waitFor(() => expect(del).toHaveBeenCalledWith('/partners/o1/api-keys/k1'))
  })

  it('does not revoke when the confirm dialog is cancelled', async () => {
    vi.stubGlobal('confirm', vi.fn(() => false))
    get.mockResolvedValue({ data: { data: [KEY] } })
    renderPage(<ApiKeysPage />)
    await screen.findByText('Backend')

    const row = screen.getByText('Backend').closest('tr')!
    await userEvent.click(within(row).getByRole('button'))
    expect(del).not.toHaveBeenCalled()
  })

  it('shows an error in the modal when issuing fails', async () => {
    get.mockResolvedValue({ data: { data: [] } })
    post.mockRejectedValue(new Error('500'))
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    renderPage(<ApiKeysPage />)
    await screen.findByText(/no api keys yet/i)

    await userEvent.click(screen.getByRole('button', { name: /issue new key/i }))
    await userEvent.type(screen.getByPlaceholderText(/production backend/i), 'My Key')
    await userEvent.click(screen.getByRole('button', { name: /^issue key$/i }))

    expect(await screen.findByText(/failed to issue key/i)).toBeInTheDocument()
    spy.mockRestore()
  })
})
