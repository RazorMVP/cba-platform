// web-react/src/shared/components/StatusBadge.test.tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StatusBadge } from './StatusBadge'

describe('StatusBadge', () => {
  it('renders the label', () => {
    render(<StatusBadge label="ACTIVE" variant="success" />)
    expect(screen.getByText('ACTIVE')).toBeInTheDocument()
  })

  it('applies success variant classes', () => {
    render(<StatusBadge label="ACTIVE" variant="success" />)
    const badge = screen.getByText('ACTIVE')
    expect(badge.className).toContain('success')
  })

  it('applies error variant classes', () => {
    render(<StatusBadge label="FAILED" variant="error" />)
    const badge = screen.getByText('FAILED')
    expect(badge.className).toContain('error')
  })
})
