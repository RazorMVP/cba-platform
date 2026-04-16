// web-react/src/app/layout/Sidebar.tsx
import { NavLink } from 'react-router-dom'
import { cn } from '@/shared/components/cn'

interface NavItem {
  label: string
  path: string
  exact?: boolean
}

interface NavSection {
  heading: string
  items: NavItem[]
}

const NAV: NavSection[] = [
  {
    heading: 'Operations',
    items: [
      { label: 'Dashboard',  path: '/dashboard', exact: true },
      { label: 'Customers',  path: '/customers' },
      { label: 'Accounts',   path: '/accounts' },
      { label: 'Loans',      path: '/loans' },
      { label: 'Payments',   path: '/payments' },
      { label: 'Tellers',    path: '/tellers' },
    ],
  },
  {
    heading: 'Products',
    items: [
      { label: 'Loan Products',       path: '/products/loan-products' },
      { label: 'Deposit Products',    path: '/products/deposit-products' },
      { label: 'Fixed Deposits',      path: '/products/fixed-deposits' },
      { label: 'Recurring Deposits',  path: '/products/recurring-deposits' },
      { label: 'Shares',              path: '/products/shares' },
    ],
  },
  {
    heading: 'Accounting',
    items: [
      { label: 'GL Accounts',          path: '/accounting/gl-accounts' },
      { label: 'Journal Entries',      path: '/accounting/journal-entries' },
      { label: 'Provisioning',         path: '/accounting/provisioning' },
      { label: 'Financial Activities', path: '/accounting/financial-activities' },
    ],
  },
  {
    heading: 'Cards',
    items: [
      { label: 'Card List',        path: '/cards', exact: true },
      { label: 'Card Products',    path: '/cards/products' },
      { label: 'Fraud Rules',      path: '/cards/fraud' },
      { label: 'Settlement',       path: '/cards/settlement' },
      { label: 'Disputes',         path: '/cards/disputes' },
      { label: 'Terminal',         path: '/cards/terminal' },
      { label: 'API Keys',         path: '/cards/api-keys' },
      { label: 'Webhooks',         path: '/cards/webhooks' },
      { label: 'BIN Management',   path: '/cards/bins' },
      { label: 'Schemes',          path: '/cards/schemes' },
      { label: 'Interchange',      path: '/cards/interchange' },
    ],
  },
  {
    heading: 'Reports',
    items: [
      { label: 'Reports',        path: '/reports', exact: true },
      { label: 'CoB Scheduler',  path: '/reports/scheduler' },
      { label: 'Mailing Jobs',   path: '/reports/mailing' },
    ],
  },
  {
    heading: 'Admin',
    items: [
      { label: 'Users',          path: '/admin/users' },
      { label: 'Roles',          path: '/admin/roles' },
      { label: 'Offices',        path: '/admin/offices' },
      { label: 'Hooks',          path: '/admin/hooks' },
      { label: 'Maker-Checker',  path: '/admin/maker-checker' },
      { label: 'Notifications',  path: '/admin/notifications' },
      { label: 'TPP Management', path: '/admin/tpp' },
    ],
  },
  {
    heading: 'Groups',
    items: [
      { label: 'Groups',   path: '/groups' },
      { label: 'Centers',  path: '/centers' },
    ],
  },
  {
    heading: 'System',
    items: [
      { label: 'Codes & Values',      path: '/system/codes' },
      { label: 'Global Config',       path: '/system/config' },
      { label: 'Floating Rates',      path: '/system/floating-rates' },
      { label: 'Taxes',               path: '/system/taxes' },
      { label: 'Account Algorithms',  path: '/system/account-algorithms' },
    ],
  },
  {
    heading: 'Open Banking',
    items: [
      { label: 'Consents', path: '/open-banking/consents' },
    ],
  },
]

export default function Sidebar() {
  return (
    <nav
      aria-label="Main navigation"
      className="fixed left-0 top-0 bottom-0 flex flex-col overflow-hidden"
      style={{ width: 'var(--sidebar-width)', background: 'var(--bg-sidebar)' }}
    >
      {/* Logo */}
      <div
        className="flex items-center gap-3 px-5 py-5 shrink-0"
        style={{ borderBottom: '1px solid rgba(255,255,255,0.07)' }}
      >
        <div
          className="flex items-center justify-center rounded-lg text-white font-display font-bold text-sm shrink-0"
          style={{
            width: 32,
            height: 32,
            background: 'var(--color-primary)',
            border: '1px solid rgba(255,255,255,0.12)',
          }}
        >
          C
        </div>
        <span className="font-display font-semibold text-white text-base tracking-tight">
          CBA
        </span>
      </div>

      {/* Nav sections */}
      <div className="flex-1 overflow-y-auto py-4 px-3">
        {NAV.map(section => (
          <div key={section.heading} className="mb-6">
            <p
              aria-hidden="true"
              className="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest"
              style={{ color: 'rgba(255,255,255,0.3)' }}
            >
              {section.heading}
            </p>
            {section.items.map(item => (
              <NavLink
                key={item.path}
                to={item.path}
                end={item.exact}
                className={({ isActive }) =>
                  cn(
                    'flex items-center px-3 py-2 rounded-md text-sm font-medium transition-colors',
                    'duration-150',
                    isActive
                      ? 'text-[var(--color-accent)] bg-white/[0.08]'
                      : 'text-white/60 hover:text-white/90 hover:bg-white/5',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
          </div>
        ))}
      </div>
    </nav>
  )
}
