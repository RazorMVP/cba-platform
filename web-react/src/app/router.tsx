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

// ── Placeholder (Open Banking routes still pending) ───────────────────────
const Placeholder       = lazy(() => import('@/app/features/placeholder/PlaceholderPage'))

// ── Groups ──────────────────────────────────────────────────────────────────
const GroupsListPage    = lazy(() => import('@/app/features/groups/GroupsListPage'))
const GroupDetailPage   = lazy(() => import('@/app/features/groups/GroupDetailPage'))
const CentersListPage   = lazy(() => import('@/app/features/groups/CentersListPage'))
const CenterDetailPage  = lazy(() => import('@/app/features/groups/CenterDetailPage'))

// ── System ──────────────────────────────────────────────────────────────────
const CodesPage              = lazy(() => import('@/app/features/system/CodesPage'))
const GlobalConfigPage       = lazy(() => import('@/app/features/system/GlobalConfigPage'))
const FloatingRatesPage      = lazy(() => import('@/app/features/system/FloatingRatesPage'))
const TaxesPage              = lazy(() => import('@/app/features/system/TaxesPage'))
const AccountAlgorithmsPage  = lazy(() => import('@/app/features/system/AccountAlgorithmsPage'))

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

// ── Accounting ──────────────────────────────────────────────────────────────
const GlAccountsPage          = lazy(() => import('@/app/features/accounting/GlAccountsPage'))
const JournalEntriesPage      = lazy(() => import('@/app/features/accounting/JournalEntriesPage'))
const ProvisioningPage        = lazy(() => import('@/app/features/accounting/ProvisioningPage'))
const FinancialActivitiesPage = lazy(() => import('@/app/features/accounting/FinancialActivitiesPage'))

// ── Reports ─────────────────────────────────────────────────────────────────
const ReportsListPage   = lazy(() => import('@/app/features/reports/ReportsListPage'))
const CobSchedulerPage  = lazy(() => import('@/app/features/reports/CobSchedulerPage'))
const ReportMailingPage = lazy(() => import('@/app/features/reports/ReportMailingPage'))

// ── Admin ───────────────────────────────────────────────────────────────────
const UsersPage        = lazy(() => import('@/app/features/admin/UsersPage'))
const RolesPage        = lazy(() => import('@/app/features/admin/RolesPage'))
const OfficesPage      = lazy(() => import('@/app/features/admin/OfficesPage'))
const HooksPage        = lazy(() => import('@/app/features/admin/HooksPage'))
const MakerCheckerPage = lazy(() => import('@/app/features/admin/MakerCheckerPage'))
const NotificationsPage = lazy(() => import('@/app/features/admin/NotificationsPage'))
const TppPage          = lazy(() => import('@/app/features/admin/TppPage'))

// ── Cards ───────────────────────────────────────────────────────────────────
const CardListPage           = lazy(() => import('@/app/features/cards/CardListPage'))
const CardDetailPage         = lazy(() => import('@/app/features/cards/CardDetailPage'))
const CardProductsPage       = lazy(() => import('@/app/features/cards/CardProductsPage'))
const FraudRulesPage         = lazy(() => import('@/app/features/cards/FraudRulesPage'))
const SettlementPage         = lazy(() => import('@/app/features/cards/SettlementPage'))
const DisputesPage           = lazy(() => import('@/app/features/cards/DisputesPage'))
const TerminalSimulatorPage  = lazy(() => import('@/app/features/cards/TerminalSimulatorPage'))
const ApiKeysPage            = lazy(() => import('@/app/features/cards/ApiKeysPage'))
const WebhooksPage           = lazy(() => import('@/app/features/cards/WebhooksPage'))
const BinManagementPage      = lazy(() => import('@/app/features/cards/BinManagementPage'))
const SchemeConfigPage       = lazy(() => import('@/app/features/cards/SchemeConfigPage'))
const InterchangePage        = lazy(() => import('@/app/features/cards/InterchangePage'))

// ── Products ────────────────────────────────────────────────────────────────
const LoanProductsListPage        = lazy(() => import('@/app/features/products/loan-products/LoanProductsListPage'))
const LoanProductDetailPage       = lazy(() => import('@/app/features/products/loan-products/LoanProductDetailPage'))
const DepositProductsListPage     = lazy(() => import('@/app/features/products/deposit-products/DepositProductsListPage'))
const DepositProductDetailPage    = lazy(() => import('@/app/features/products/deposit-products/DepositProductDetailPage'))
const FixedDepositsListPage       = lazy(() => import('@/app/features/products/fixed-deposits/FixedDepositsListPage'))
const FixedDepositDetailPage      = lazy(() => import('@/app/features/products/fixed-deposits/FixedDepositDetailPage'))
const RecurringDepositsListPage   = lazy(() => import('@/app/features/products/recurring-deposits/RecurringDepositsListPage'))
const RecurringDepositDetailPage  = lazy(() => import('@/app/features/products/recurring-deposits/RecurringDepositDetailPage'))
const SharesListPage              = lazy(() => import('@/app/features/products/shares/SharesListPage'))
const ShareDetailPage             = lazy(() => import('@/app/features/products/shares/ShareDetailPage'))

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
      { path: 'products/loan-products',          element: page(LoanProductsListPage) },
      { path: 'products/loan-products/:id',      element: page(LoanProductDetailPage) },
      { path: 'products/deposit-products',       element: page(DepositProductsListPage) },
      { path: 'products/deposit-products/:id',   element: page(DepositProductDetailPage) },
      { path: 'products/fixed-deposits',         element: page(FixedDepositsListPage) },
      { path: 'products/fixed-deposits/:id',     element: page(FixedDepositDetailPage) },
      { path: 'products/recurring-deposits',     element: page(RecurringDepositsListPage) },
      { path: 'products/recurring-deposits/:id', element: page(RecurringDepositDetailPage) },
      { path: 'products/shares',                 element: page(SharesListPage) },
      { path: 'products/shares/:id',             element: page(ShareDetailPage) },

      // Accounting
      { path: 'accounting/gl-accounts',          element: page(GlAccountsPage) },
      { path: 'accounting/journal-entries',      element: page(JournalEntriesPage) },
      { path: 'accounting/provisioning',         element: page(ProvisioningPage) },
      { path: 'accounting/financial-activities', element: page(FinancialActivitiesPage) },

      // Cards — static sub-routes before :id
      { path: 'cards',                           element: page(CardListPage) },
      { path: 'cards/products',                  element: page(CardProductsPage) },
      { path: 'cards/fraud',                     element: page(FraudRulesPage) },
      { path: 'cards/settlement',                element: page(SettlementPage) },
      { path: 'cards/disputes',                  element: page(DisputesPage) },
      { path: 'cards/terminal',                  element: page(TerminalSimulatorPage) },
      { path: 'cards/api-keys',                  element: page(ApiKeysPage) },
      { path: 'cards/webhooks',                  element: page(WebhooksPage) },
      { path: 'cards/bins',                      element: page(BinManagementPage) },
      { path: 'cards/schemes',                   element: page(SchemeConfigPage) },
      { path: 'cards/interchange',               element: page(InterchangePage) },
      { path: 'cards/:id',                       element: page(CardDetailPage) },

      // Reports
      { path: 'reports',                         element: page(ReportsListPage) },
      { path: 'reports/scheduler',               element: page(CobSchedulerPage) },
      { path: 'reports/mailing',                 element: page(ReportMailingPage) },

      // Admin
      { path: 'admin/users',                     element: page(UsersPage) },
      { path: 'admin/roles',                     element: page(RolesPage) },
      { path: 'admin/offices',                   element: page(OfficesPage) },
      { path: 'admin/hooks',                     element: page(HooksPage) },
      { path: 'admin/maker-checker',             element: page(MakerCheckerPage) },
      { path: 'admin/notifications',             element: page(NotificationsPage) },
      { path: 'admin/tpp',                       element: page(TppPage) },

      // Groups
      { path: 'groups',                          element: page(GroupsListPage) },
      { path: 'groups/:id',                      element: page(GroupDetailPage) },
      { path: 'centers',                         element: page(CentersListPage) },
      { path: 'centers/:id',                     element: page(CenterDetailPage) },

      // System
      { path: 'system/codes',                    element: page(CodesPage) },
      { path: 'system/config',                   element: page(GlobalConfigPage) },
      { path: 'system/floating-rates',           element: page(FloatingRatesPage) },
      { path: 'system/taxes',                    element: page(TaxesPage) },
      { path: 'system/account-algorithms',       element: page(AccountAlgorithmsPage) },

      // Open Banking
      { path: 'open-banking/consents',           element: page(Placeholder) },
      { path: 'open-banking/consents/:id',       element: page(Placeholder) },

      // Catch-all
      { path: '*', element: <Navigate to="/dashboard" replace /> },
    ],
  },
])
