// web-react/src/app/router.tsx
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import Shell from '@/app/layout/Shell'

const Placeholder = lazy(() => import('@/app/features/placeholder/PlaceholderPage'))

function Page() {
  return (
    <Suspense fallback={<div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>}>
      <Placeholder />
    </Suspense>
  )
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Shell />,
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },

      // Operations
      { path: 'dashboard',                      element: <Page /> },
      { path: 'customers',                       element: <Page /> },
      { path: 'customers/:id',                   element: <Page /> },
      { path: 'accounts',                        element: <Page /> },
      { path: 'accounts/:id',                    element: <Page /> },
      { path: 'payments',                        element: <Page /> },
      { path: 'payments/:id',                    element: <Page /> },
      { path: 'loans',                           element: <Page /> },
      { path: 'loans/:id',                       element: <Page /> },
      { path: 'tellers',                         element: <Page /> },
      { path: 'tellers/:id',                     element: <Page /> },

      // Products
      { path: 'products/loan-products',          element: <Page /> },
      { path: 'products/loan-products/:id',      element: <Page /> },
      { path: 'products/deposit-products',       element: <Page /> },
      { path: 'products/deposit-products/:id',   element: <Page /> },
      { path: 'products/fixed-deposits',         element: <Page /> },
      { path: 'products/fixed-deposits/:id',     element: <Page /> },
      { path: 'products/recurring-deposits',     element: <Page /> },
      { path: 'products/recurring-deposits/:id', element: <Page /> },
      { path: 'products/shares',                 element: <Page /> },
      { path: 'products/shares/:id',             element: <Page /> },

      // Accounting
      { path: 'accounting/gl-accounts',          element: <Page /> },
      { path: 'accounting/journal-entries',      element: <Page /> },
      { path: 'accounting/provisioning',         element: <Page /> },
      { path: 'accounting/financial-activities', element: <Page /> },

      // Cards — specific routes BEFORE the :id catch (React Router v6 matches in order)
      { path: 'cards',                           element: <Page /> },
      { path: 'cards/products',                  element: <Page /> },
      { path: 'cards/fraud',                     element: <Page /> },
      { path: 'cards/settlement',                element: <Page /> },
      { path: 'cards/disputes',                  element: <Page /> },
      { path: 'cards/terminal',                  element: <Page /> },
      { path: 'cards/api-keys',                  element: <Page /> },
      { path: 'cards/webhooks',                  element: <Page /> },
      { path: 'cards/bins',                      element: <Page /> },
      { path: 'cards/schemes',                   element: <Page /> },
      { path: 'cards/interchange',               element: <Page /> },
      { path: 'cards/:id',                       element: <Page /> },

      // Reports
      { path: 'reports',                         element: <Page /> },
      { path: 'reports/scheduler',               element: <Page /> },
      { path: 'reports/mailing',                 element: <Page /> },

      // Admin
      { path: 'admin/users',                     element: <Page /> },
      { path: 'admin/roles',                     element: <Page /> },
      { path: 'admin/offices',                   element: <Page /> },
      { path: 'admin/hooks',                     element: <Page /> },
      { path: 'admin/maker-checker',             element: <Page /> },
      { path: 'admin/notifications',             element: <Page /> },
      { path: 'admin/tpp',                       element: <Page /> },

      // Groups
      { path: 'groups',                          element: <Page /> },
      { path: 'groups/:id',                      element: <Page /> },
      { path: 'centers',                         element: <Page /> },
      { path: 'centers/:id',                     element: <Page /> },

      // System
      { path: 'system/codes',                    element: <Page /> },
      { path: 'system/config',                   element: <Page /> },
      { path: 'system/floating-rates',           element: <Page /> },
      { path: 'system/taxes',                    element: <Page /> },
      { path: 'system/account-algorithms',       element: <Page /> },

      // Open Banking
      { path: 'open-banking/consents',           element: <Page /> },
      { path: 'open-banking/consents/:id',       element: <Page /> },

      // Catch-all
      { path: '*', element: <Navigate to="/dashboard" replace /> },
    ],
  },
])
