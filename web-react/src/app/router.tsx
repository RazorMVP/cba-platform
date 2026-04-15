// web-react/src/app/router.tsx
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import type { ComponentType } from 'react'
import Shell from '@/app/layout/Shell'

// ── Shared fallback ────────────────────────────────────────────────────────
const Loading = () => (
  <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
)

function page(C: ReturnType<typeof lazy<ComponentType>>) {
  return (
    <Suspense fallback={<Loading />}>
      <C />
    </Suspense>
  )
}

// ── Placeholder (non-Operations routes still pending) ──────────────────────
const Placeholder       = lazy(() => import('@/app/features/placeholder/PlaceholderPage'))

// ── Operations ─────────────────────────────────────────────────────────────
const DashboardPage     = lazy(() => import('@/app/features/operations/dashboard/DashboardPage'))
const CustomersListPage = lazy(() => import('@/app/features/operations/customers/CustomersListPage'))
const CustomerDetail    = lazy(() => import('@/app/features/operations/customers/CustomerDetailPage'))
const AccountsListPage  = lazy(() => import('@/app/features/operations/accounts/AccountsListPage'))
const AccountDetail     = lazy(() => import('@/app/features/operations/accounts/AccountDetailPage'))
const PaymentsListPage  = lazy(() => import('@/app/features/operations/payments/PaymentsListPage'))
const PaymentDetail     = lazy(() => import('@/app/features/operations/payments/PaymentDetailPage'))
const LoansListPage     = lazy(() => import('@/app/features/operations/loans/LoansListPage'))
const LoanDetail        = lazy(() => import('@/app/features/operations/loans/LoanDetailPage'))
const TellersListPage   = lazy(() => import('@/app/features/operations/tellers/TellersListPage'))
const TellerDetail      = lazy(() => import('@/app/features/operations/tellers/TellerDetailPage'))

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Shell />,
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },

      // Operations
      { path: 'dashboard',    element: page(DashboardPage) },
      { path: 'customers',    element: page(CustomersListPage) },
      { path: 'customers/:id',element: page(CustomerDetail) },
      { path: 'accounts',     element: page(AccountsListPage) },
      { path: 'accounts/:id', element: page(AccountDetail) },
      { path: 'payments',     element: page(PaymentsListPage) },
      { path: 'payments/:id', element: page(PaymentDetail) },
      { path: 'loans',        element: page(LoansListPage) },
      { path: 'loans/:id',    element: page(LoanDetail) },
      { path: 'tellers',      element: page(TellersListPage) },
      { path: 'tellers/:id',  element: page(TellerDetail) },

      // Products
      { path: 'products/loan-products',          element: page(Placeholder) },
      { path: 'products/loan-products/:id',      element: page(Placeholder) },
      { path: 'products/deposit-products',       element: page(Placeholder) },
      { path: 'products/deposit-products/:id',   element: page(Placeholder) },
      { path: 'products/fixed-deposits',         element: page(Placeholder) },
      { path: 'products/fixed-deposits/:id',     element: page(Placeholder) },
      { path: 'products/recurring-deposits',     element: page(Placeholder) },
      { path: 'products/recurring-deposits/:id', element: page(Placeholder) },
      { path: 'products/shares',                 element: page(Placeholder) },
      { path: 'products/shares/:id',             element: page(Placeholder) },

      // Accounting
      { path: 'accounting/gl-accounts',          element: page(Placeholder) },
      { path: 'accounting/journal-entries',      element: page(Placeholder) },
      { path: 'accounting/provisioning',         element: page(Placeholder) },
      { path: 'accounting/financial-activities', element: page(Placeholder) },

      // Cards — static sub-routes before :id
      { path: 'cards',                           element: page(Placeholder) },
      { path: 'cards/products',                  element: page(Placeholder) },
      { path: 'cards/fraud',                     element: page(Placeholder) },
      { path: 'cards/settlement',                element: page(Placeholder) },
      { path: 'cards/disputes',                  element: page(Placeholder) },
      { path: 'cards/terminal',                  element: page(Placeholder) },
      { path: 'cards/api-keys',                  element: page(Placeholder) },
      { path: 'cards/webhooks',                  element: page(Placeholder) },
      { path: 'cards/bins',                      element: page(Placeholder) },
      { path: 'cards/schemes',                   element: page(Placeholder) },
      { path: 'cards/interchange',               element: page(Placeholder) },
      { path: 'cards/:id',                       element: page(Placeholder) },

      // Reports
      { path: 'reports',                         element: page(Placeholder) },
      { path: 'reports/scheduler',               element: page(Placeholder) },
      { path: 'reports/mailing',                 element: page(Placeholder) },

      // Admin
      { path: 'admin/users',                     element: page(Placeholder) },
      { path: 'admin/roles',                     element: page(Placeholder) },
      { path: 'admin/offices',                   element: page(Placeholder) },
      { path: 'admin/hooks',                     element: page(Placeholder) },
      { path: 'admin/maker-checker',             element: page(Placeholder) },
      { path: 'admin/notifications',             element: page(Placeholder) },
      { path: 'admin/tpp',                       element: page(Placeholder) },

      // Groups
      { path: 'groups',                          element: page(Placeholder) },
      { path: 'groups/:id',                      element: page(Placeholder) },
      { path: 'centers',                         element: page(Placeholder) },
      { path: 'centers/:id',                     element: page(Placeholder) },

      // System
      { path: 'system/codes',                    element: page(Placeholder) },
      { path: 'system/config',                   element: page(Placeholder) },
      { path: 'system/floating-rates',           element: page(Placeholder) },
      { path: 'system/taxes',                    element: page(Placeholder) },
      { path: 'system/account-algorithms',       element: page(Placeholder) },

      // Open Banking
      { path: 'open-banking/consents',           element: page(Placeholder) },
      { path: 'open-banking/consents/:id',       element: page(Placeholder) },

      // Catch-all
      { path: '*', element: <Navigate to="/dashboard" replace /> },
    ],
  },
])
