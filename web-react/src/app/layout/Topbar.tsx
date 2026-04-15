// web-react/src/app/layout/Topbar.tsx
import { useLocation } from 'react-router-dom'

// Map path prefixes to human-readable section names
const SECTION_LABELS: [string, string][] = [
  ['/dashboard',              'Dashboard'],
  ['/customers',              'Customers'],
  ['/accounts',               'Accounts'],
  ['/loans',                  'Loans'],
  ['/payments',               'Payments'],
  ['/tellers',                'Tellers'],
  ['/products/loan-products', 'Loan Products'],
  ['/products/deposit',       'Deposit Products'],
  ['/products/fixed',         'Fixed Deposits'],
  ['/products/recurring',     'Recurring Deposits'],
  ['/products/shares',        'Share Products'],
  ['/accounting',             'Accounting'],
  ['/cards/products',         'Card Products'],
  ['/cards/fraud',            'Fraud Rules'],
  ['/cards/settlement',       'Settlement'],
  ['/cards/disputes',         'Disputes'],
  ['/cards/terminal',         'Terminal Simulator'],
  ['/cards/api-keys',         'API Keys'],
  ['/cards/webhooks',         'Webhooks'],
  ['/cards/bins',             'BIN Management'],
  ['/cards/schemes',          'Scheme Config'],
  ['/cards/interchange',      'Interchange'],
  ['/cards',                  'Cards'],
  ['/reports/scheduler',      'CoB Scheduler'],
  ['/reports/mailing',        'Report Mailing'],
  ['/reports',                'Reports'],
  ['/admin/users',            'Users'],
  ['/admin/roles',            'Roles'],
  ['/admin/offices',          'Offices'],
  ['/admin/hooks',            'Hooks'],
  ['/admin/maker-checker',    'Maker-Checker'],
  ['/admin/notifications',    'Notifications'],
  ['/admin/tpp',              'TPP Management'],
  ['/groups',                 'Groups'],
  ['/centers',                'Centers'],
  ['/system/codes',           'Codes & Values'],
  ['/system/config',          'Global Config'],
  ['/system/floating-rates',  'Floating Rates'],
  ['/system/taxes',           'Taxes'],
  ['/system/account-algorithms', 'Account Algorithms'],
  ['/open-banking',           'Open Banking'],
]

export function getSectionLabel(pathname: string): string {
  for (const [prefix, label] of SECTION_LABELS) {
    if (pathname.startsWith(prefix)) return label
  }
  return 'CBA Backoffice'
}

export default function Topbar() {
  const { pathname } = useLocation()
  const label = getSectionLabel(pathname)

  return (
    <header
      className="fixed top-0 right-0 flex items-center justify-between px-6 z-10"
      style={{
        left: 'var(--sidebar-width)',
        height: 'var(--topbar-height)',
        background: 'var(--bg-app)',
        borderBottom: '1px solid var(--color-border)',
      }}
    >
      <p className="font-display font-semibold text-lg" style={{ color: 'var(--color-text)' }}>
        {label}
      </p>
      <div
        className="flex items-center justify-center rounded-full font-semibold text-sm shrink-0"
        style={{
          width: 36,
          height: 36,
          background: 'var(--color-primary)',
          color: 'white',
        }}
      >
        A
      </div>
    </header>
  )
}
