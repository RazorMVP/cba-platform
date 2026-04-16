// web-react/src/app/layout/Topbar.test.tsx
import { describe, it, expect } from 'vitest'
import { getSectionLabel } from './Topbar'

describe('getSectionLabel', () => {
  it('returns the correct label for exact paths', () => {
    expect(getSectionLabel('/dashboard')).toBe('Dashboard')
    expect(getSectionLabel('/customers')).toBe('Customers')
    expect(getSectionLabel('/accounts')).toBe('Accounts')
    expect(getSectionLabel('/loans')).toBe('Loans')
    expect(getSectionLabel('/payments')).toBe('Payments')
    expect(getSectionLabel('/tellers')).toBe('Tellers')
  })

  it('resolves /cards sub-routes before the generic /cards catch', () => {
    expect(getSectionLabel('/cards/products')).toBe('Card Products')
    expect(getSectionLabel('/cards/fraud')).toBe('Fraud Rules')
    expect(getSectionLabel('/cards/settlement')).toBe('Settlement')
    expect(getSectionLabel('/cards/disputes')).toBe('Disputes')
    expect(getSectionLabel('/cards/terminal')).toBe('Terminal Simulator')
    expect(getSectionLabel('/cards/api-keys')).toBe('API Keys')
    expect(getSectionLabel('/cards/webhooks')).toBe('Webhooks')
    expect(getSectionLabel('/cards/bins')).toBe('BIN Management')
    expect(getSectionLabel('/cards/schemes')).toBe('Scheme Config')
    expect(getSectionLabel('/cards/interchange')).toBe('Interchange')
    // generic /cards must still match for the list route and detail routes
    expect(getSectionLabel('/cards')).toBe('Cards')
    expect(getSectionLabel('/cards/abc-123')).toBe('Cards')
  })

  it('resolves /reports sub-routes before the generic /reports catch', () => {
    expect(getSectionLabel('/reports/scheduler')).toBe('CoB Scheduler')
    expect(getSectionLabel('/reports/mailing')).toBe('Report Mailing')
    expect(getSectionLabel('/reports')).toBe('Reports')
  })

  it('handles nested admin, system, and open-banking paths', () => {
    expect(getSectionLabel('/admin/users')).toBe('Users')
    expect(getSectionLabel('/admin/maker-checker')).toBe('Maker-Checker')
    expect(getSectionLabel('/admin/tpp')).toBe('TPP Management')
    expect(getSectionLabel('/system/codes')).toBe('Codes & Values')
    expect(getSectionLabel('/system/account-algorithms')).toBe('Account Algorithms')
    expect(getSectionLabel('/open-banking/consents')).toBe('Open Banking')
    expect(getSectionLabel('/open-banking/consents/abc-123')).toBe('Open Banking')
  })

  it('matches on path prefix so detail routes inherit the section label', () => {
    expect(getSectionLabel('/customers/abc-123')).toBe('Customers')
    expect(getSectionLabel('/loans/abc-123')).toBe('Loans')
    expect(getSectionLabel('/groups/abc-123')).toBe('Groups')
  })

  it('falls back to CBA Backoffice for unknown paths', () => {
    expect(getSectionLabel('/')).toBe('CBA Backoffice')
    expect(getSectionLabel('/unknown')).toBe('CBA Backoffice')
  })
})
