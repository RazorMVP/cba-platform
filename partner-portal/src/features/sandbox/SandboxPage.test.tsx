import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SandboxPage from './SandboxPage'

// SandboxPage is static content (no apiClient / no auth) — the only logic is the
// per-row "copy to clipboard" handler, which these tests exercise.

describe('SandboxPage', () => {
  beforeEach(() => {
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } })
  })

  it('renders the pre-seeded test data and quick-start samples', () => {
    render(<SandboxPage />)
    expect(screen.getByRole('heading', { name: 'Sandbox' })).toBeInTheDocument()
    expect(screen.getByText('cust_demo_001')).toBeInTheDocument()
    expect(screen.getByText('acct_demo_savings')).toBeInTheDocument()
    expect(screen.getByText('Get an access token')).toBeInTheDocument()
  })

  it('copies a test-data ID to the clipboard', async () => {
    render(<SandboxPage />)
    const row = screen.getByText('cust_demo_001').closest('tr')!
    await userEvent.click(within(row).getByRole('button'))
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('cust_demo_001')
  })

  it('copies a code sample to the clipboard', async () => {
    render(<SandboxPage />)
    const header = screen.getByText('Get an access token').closest('div')!
    await userEvent.click(within(header).getByRole('button'))
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
      expect.stringContaining('partners/auth/login'),
    )
  })
})
