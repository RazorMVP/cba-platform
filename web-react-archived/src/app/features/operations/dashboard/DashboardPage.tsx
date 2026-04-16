// web-react/src/app/features/operations/dashboard/DashboardPage.tsx
import { Link } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { KpiCard } from '@/shared/components/KpiCard'
import { DataTable, type ColumnDef } from '@/shared/components/DataTable'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { useDashboardStats, useKycQueue } from '../api/useDashboard'
import { useLoans } from '../api/useLoans'
import type { Customer, Loan } from '../api/types'

function fmt(n: number) {
  if (n >= 1_000_000) return `$${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `$${(n / 1_000).toFixed(1)}K`
  return `$${n}`
}

function kycVariant(status: string) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'PENDING_KYC') return 'warning'
  if (status === 'SUSPENDED' || status === 'REJECTED') return 'error'
  if (status === 'CLOSED') return 'neutral'
  return 'info'
}

function loanVariant(status: string) {
  if (status === 'ACTIVE' || status === 'CLOSED_OBLIGATIONS_MET') return 'success'
  if (status === 'IN_ARREARS') return 'error'
  if (status === 'APPROVED' || status === 'DISBURSED') return 'info'
  if (status === 'SUBMITTED' || status === 'UNDER_REVIEW') return 'warning'
  return 'neutral'
}

const kycColumns: ColumnDef<Customer>[] = [
  {
    key: 'name',
    header: 'Customer',
    cell: row => (
      <Link to={`/customers/${row.id}`} className="font-medium hover:underline" style={{ color: 'var(--color-info)' }}>
        {row.firstName} {row.lastName}
      </Link>
    ),
  },
  { key: 'email', header: 'Email', cell: row => row.email },
  {
    key: 'status',
    header: 'KYC Status',
    cell: row => <StatusBadge label={row.kycStatus.replace(/_/g, ' ')} variant={kycVariant(row.kycStatus)} />,
  },
]

const loanColumns: ColumnDef<Loan>[] = [
  {
    key: 'ref',
    header: 'Loan #',
    cell: row => (
      <Link to={`/loans/${row.id}`} className="font-medium hover:underline" style={{ color: 'var(--color-info)' }}>
        {row.loanAccountNumber}
      </Link>
    ),
  },
  { key: 'customer', header: 'Customer', cell: row => row.customerName },
  {
    key: 'amount',
    header: 'Outstanding',
    numeric: true,
    cell: row => (
      <span className="tabular-nums">{fmt(row.outstandingBalance)} <span style={{ color: 'var(--color-muted)' }}>{row.currencyCode}</span></span>
    ),
  },
  {
    key: 'status',
    header: 'Status',
    cell: row => <StatusBadge label={row.status.replace(/_/g, ' ')} variant={loanVariant(row.status)} />,
  },
]

export default function DashboardPage() {
  const stats = useDashboardStats()
  const kycQueue = useKycQueue()
  const recentLoans = useLoans({ page: 0, size: 5 })

  const s = stats.data

  return (
    <div>
      <PageHeader
        title="Dashboard"
        subtitle="Platform overview — real-time snapshot"
      />

      {/* KPI cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <KpiCard
          label="Total Customers"
          value={stats.isLoading ? '—' : (s?.totalCustomers ?? 0).toLocaleString()}
          delta={s?.customerGrowthPct}
          deltaPositive={true}
        />
        <KpiCard
          label="Active Loans"
          value={stats.isLoading ? '—' : (s?.activeLoans ?? 0).toLocaleString()}
          delta={s?.loanGrowthPct}
          deltaPositive={true}
        />
        <KpiCard
          label="Total Accounts"
          value={stats.isLoading ? '—' : (s?.totalDeposits ?? 0).toLocaleString()}
          delta={s?.depositGrowthPct}
          deltaPositive={true}
        />
        <KpiCard
          label="Disbursed MTD"
          value={stats.isLoading ? '—' : fmt(s?.loanDisbursedMtd ?? 0)}
          delta={s?.disbursedGrowthPct}
          deltaPositive={true}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* KYC Queue */}
        <section
          className="rounded-xl"
          style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}
        >
          <div className="flex items-center justify-between px-6 py-4" style={{ borderBottom: '1px solid var(--color-border)' }}>
            <h2 className="font-display font-semibold text-sm" style={{ color: 'var(--color-text)' }}>
              KYC Queue
            </h2>
            <Link to="/customers?kycStatus=PENDING_KYC" className="text-xs font-medium" style={{ color: 'var(--color-info)' }}>
              View all →
            </Link>
          </div>
          <DataTable
            columns={kycColumns}
            data={kycQueue.data ?? []}
            loading={kycQueue.isLoading}
            emptyMessage="No pending KYC reviews"
            getRowKey={r => r.id}
          />
        </section>

        {/* Recent Loans */}
        <section
          className="rounded-xl"
          style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}
        >
          <div className="flex items-center justify-between px-6 py-4" style={{ borderBottom: '1px solid var(--color-border)' }}>
            <h2 className="font-display font-semibold text-sm" style={{ color: 'var(--color-text)' }}>
              Recent Loans
            </h2>
            <Link to="/loans" className="text-xs font-medium" style={{ color: 'var(--color-info)' }}>
              View all →
            </Link>
          </div>
          <DataTable
            columns={loanColumns}
            data={recentLoans.data?.data ?? []}
            loading={recentLoans.isLoading}
            emptyMessage="No loans found"
            getRowKey={r => r.id}
          />
        </section>
      </div>

      {/* Loan Portfolio Distribution */}
      {!recentLoans.isLoading && (recentLoans.data?.data.length ?? 0) > 0 && (
        <section
          className="mt-6 rounded-xl p-6"
          style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}
        >
          <h2 className="font-display font-semibold text-sm mb-4" style={{ color: 'var(--color-text)' }}>
            Portfolio by Status
          </h2>
          <PortfolioBars loans={recentLoans.data?.data ?? []} />
        </section>
      )}
    </div>
  )
}

function PortfolioBars({ loans }: { loans: Loan[] }) {
  const counts: Record<string, number> = {}
  for (const l of loans) { counts[l.status] = (counts[l.status] ?? 0) + 1 }
  const total = loans.length || 1
  const entries = Object.entries(counts)

  const colors: Record<string, string> = {
    ACTIVE: 'var(--color-success)',
    IN_ARREARS: 'var(--color-error)',
    DISBURSED: 'var(--color-info)',
    APPROVED: 'var(--color-info)',
    SUBMITTED: 'var(--color-warning)',
    UNDER_REVIEW: 'var(--color-warning)',
    CLOSED_OBLIGATIONS_MET: 'var(--color-muted)',
  }

  return (
    <div className="space-y-3">
      {entries.map(([status, count]) => (
        <div key={status}>
          <div className="flex justify-between text-xs mb-1" style={{ color: 'var(--color-muted)' }}>
            <span>{status.replace(/_/g, ' ')}</span>
            <span className="tabular-nums">{count} ({Math.round((count / total) * 100)}%)</span>
          </div>
          <div className="h-2 rounded-full" style={{ background: 'var(--bg-subtle)' }}>
            <div
              className="h-2 rounded-full transition-all"
              style={{
                width: `${(count / total) * 100}%`,
                background: colors[status] ?? 'var(--color-muted)',
                transition: `width 0.6s var(--ease-out-quint)`,
              }}
            />
          </div>
        </div>
      ))}
    </div>
  )
}
