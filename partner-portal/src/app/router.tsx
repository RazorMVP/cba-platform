import { createBrowserRouter, Navigate } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import { AppShell } from '../shared/components/AppShell'
import { AuthGuard } from '../shared/components/AuthGuard'
import { StaffGuard } from '../shared/components/StaffGuard'

const LoginPage = lazy(() => import('../features/auth/LoginPage'))
const RegisterPage = lazy(() => import('../features/auth/RegisterPage'))
const DashboardPage = lazy(() => import('../features/dashboard/DashboardPage'))
const ApiKeysPage = lazy(() => import('../features/api-keys/ApiKeysPage'))
const WebhooksPage = lazy(() => import('../features/webhooks/WebhooksPage'))
const ConsentsPage = lazy(() => import('../features/consents/ConsentsPage'))
const SandboxPage = lazy(() => import('../features/sandbox/SandboxPage'))
const ApplyPage = lazy(() => import('../features/apply/ApplyPage'))
const PartnerMgmtPage = lazy(() => import('../features/partner-mgmt/PartnerMgmtPage'))
const UsageAnalyticsPage = lazy(() => import('../features/usage-analytics/UsageAnalyticsPage'))
const SettingsPage = lazy(() => import('../features/settings/SettingsPage'))

const Loader = () => (
  <div className="flex items-center justify-center h-screen bg-[#040609]">
    <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
  </div>
)

export const router = createBrowserRouter([
  { path: '/login', element: <Suspense fallback={<Loader />}><LoginPage /></Suspense> },
  { path: '/register', element: <Suspense fallback={<Loader />}><RegisterPage /></Suspense> },
  {
    path: '/',
    element: <AuthGuard><AppShell /></AuthGuard>,
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: 'dashboard', element: <Suspense fallback={<Loader />}><DashboardPage /></Suspense> },
      { path: 'api-keys', element: <Suspense fallback={<Loader />}><ApiKeysPage /></Suspense> },
      { path: 'webhooks', element: <Suspense fallback={<Loader />}><WebhooksPage /></Suspense> },
      { path: 'consents', element: <Suspense fallback={<Loader />}><ConsentsPage /></Suspense> },
      { path: 'sandbox', element: <Suspense fallback={<Loader />}><SandboxPage /></Suspense> },
      { path: 'apply', element: <Suspense fallback={<Loader />}><ApplyPage /></Suspense> },
      { path: 'settings', element: <Suspense fallback={<Loader />}><SettingsPage /></Suspense> },
      {
        path: 'partner-management',
        element: <StaffGuard><Suspense fallback={<Loader />}><PartnerMgmtPage /></Suspense></StaffGuard>,
      },
      {
        path: 'usage-analytics',
        element: <StaffGuard><Suspense fallback={<Loader />}><UsageAnalyticsPage /></Suspense></StaffGuard>,
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])
