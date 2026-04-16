// web-react/src/app/features/operations/loans/LoansListPage.tsx
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { DataTable, type ColumnDef } from '@/shared/components/DataTable'
import { StatusBadge, type BadgeVariant } from '@/shared/components/StatusBadge'
import { useLoans } from '../api/useLoans'
import type { Loan, LoanStatus } from '../api/types'

const STATUS_TABS = [
  { label: 'All', value: '' },
  { label: 'Submitted', value: 'SUBMITTED' },
  { label: 'Under Review', value: 'UNDER_REVIEW' },
  { label: 'Approved', value: 'APPROVED' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'In Arrears', value: 'IN_ARREARS' },
  { label: 'Closed', value: 'CLOSED_OBLIGATIONS_MET' },
]

function loanVariant(s: LoanStatus): BadgeVariant {
  const m: Partial<Record<LoanStatus, BadgeVariant>> = {
    ACTIVE: 'success', CLOSED_OBLIGATIONS_MET: 'success',
    IN_ARREARS: 'error', WRITTEN_OFF: 'error', REJECTED: 'error',
    DISBURSED: 'info', APPROVED: 'info',
    SUBMITTED: 'warning', UNDER_REVIEW: 'warning',
  }
  return m[s] ?? 'neutral'
}

const PAGE_SIZE = 20

const columns: ColumnDef<Loan>[] = [
  {
    key: 'num', header: 'Loan #',
    cell: r => <Link to={`/loans/${r.id}`} className="font-medium hover:underline" style={{ color: 'var(--color-info)' }}>{r.loanAccountNumber}</Link>,
  },
  { key: 'customer', header: 'Customer', cell: r => r.customerName },
  { key: 'product', header: 'Product', cell: r => r.productName },
  {
    key: 'principal', header: 'Principal', numeric: true,
    cell: r => <span className="tabular-nums">{r.principalAmount.toLocaleString()} <span style={{ color: 'var(--color-muted)' }}>{r.currencyCode}</span></span>,
  },
  {
    key: 'outstanding', header: 'Outstanding', numeric: true,
    cell: r => <span className="tabular-nums">{r.outstandingBalance.toLocaleString()} <span style={{ color: 'var(--color-muted)' }}>{r.currencyCode}</span></span>,
  },
  { key: 'rate', header: 'Rate', numeric: true, cell: r => `${r.interestRate}%` },
  { key: 'status', header: 'Status', cell: r => <StatusBadge label={r.status.replace(/_/g, ' ')} variant={loanVariant(r.status)} /> },
]

export default function LoansListPage() {
  const navigate = useNavigate()
  const [activeStatus, setActiveStatus] = useState('')
  const [page, setPage] = useState(0)

  const { data, isLoading } = useLoans({ page, size: PAGE_SIZE, status: activeStatus || undefined })
  const loans = data?.data ?? []
  const total = data?.meta?.total ?? 0
  const totalPages = Math.ceil(total / PAGE_SIZE)

  return (
    <div>
      <PageHeader
        title="Loans"
        subtitle={`${total.toLocaleString()} total`}
        actions={
          <button onClick={() => navigate('/loans/new')}
            className="px-4 py-2 rounded-lg text-sm font-medium text-white"
            style={{ background: 'var(--color-primary)' }}>
            + New Application
          </button>
        }
      />

      {/* Status filter tabs */}
      <div className="flex flex-wrap gap-1 mb-6 rounded-lg p-1 w-fit" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        {STATUS_TABS.map(t => (
          <button key={t.value} onClick={() => { setActiveStatus(t.value); setPage(0) }}
            className="px-3 py-1 rounded-md text-xs font-medium transition-colors"
            style={{ background: activeStatus === t.value ? 'var(--color-primary)' : 'transparent', color: activeStatus === t.value ? '#fff' : 'var(--color-muted)' }}>
            {t.label}
          </button>
        ))}
      </div>

      <div className="rounded-xl" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <DataTable columns={columns} data={loans} loading={isLoading}
          emptyMessage="No loans match your filters" getRowKey={r => r.id} />
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-6 py-3" style={{ borderTop: '1px solid var(--color-border)' }}>
            <span className="text-xs tabular-nums" style={{ color: 'var(--color-muted)' }}>Page {page + 1} of {totalPages} · {total.toLocaleString()}</span>
            <div className="flex gap-2">
              <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="px-3 py-1 text-xs rounded-md disabled:opacity-40" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>← Prev</button>
              <button disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)} className="px-3 py-1 text-xs rounded-md disabled:opacity-40" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Next →</button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
