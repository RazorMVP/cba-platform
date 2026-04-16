// web-react/src/app/features/operations/accounts/AccountsListPage.tsx
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { DataTable, type ColumnDef } from '@/shared/components/DataTable'
import { StatusBadge, type BadgeVariant } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useAccounts, useCreateAccount } from '../api/useAccounts'
import type { Account, AccountStatus, AccountType } from '../api/types'

const TYPE_TABS = [
  { label: 'All', value: '' },
  { label: 'Savings', value: 'SAVINGS' },
  { label: 'Checking', value: 'CHECKING' },
  { label: 'Fixed Deposit', value: 'FIXED_DEPOSIT' },
]

function statusVariant(s: AccountStatus): BadgeVariant {
  const m: Record<AccountStatus, BadgeVariant> = {
    ACTIVE: 'success', INACTIVE: 'warning', CLOSED: 'neutral', FROZEN: 'error',
  }
  return m[s]
}

const PAGE_SIZE = 20

const columns: ColumnDef<Account>[] = [
  {
    key: 'num', header: 'Account #',
    cell: r => <Link to={`/accounts/${r.id}`} className="font-medium hover:underline" style={{ color: 'var(--color-info)' }}>{r.accountNumber}</Link>,
  },
  { key: 'customer', header: 'Customer', cell: r => r.customerName },
  { key: 'type', header: 'Type', cell: r => r.accountType.replace(/_/g, ' ') },
  { key: 'product', header: 'Product', cell: r => r.productName },
  {
    key: 'balance', header: 'Balance', numeric: true,
    cell: r => <span className="tabular-nums">{r.balance.toLocaleString(undefined, { minimumFractionDigits: 2 })} <span style={{ color: 'var(--color-muted)' }}>{r.currencyCode}</span></span>,
  },
  { key: 'status', header: 'Status', cell: r => <StatusBadge label={r.status} variant={statusVariant(r.status)} /> },
  { key: 'created', header: 'Opened', numeric: true, cell: r => new Date(r.createdAt).toLocaleDateString() },
]

export default function AccountsListPage() {
  const navigate = useNavigate()
  const [activeType, setActiveType] = useState<string>('')
  const [page, setPage] = useState(0)
  const [showCreate, setShowCreate] = useState(false)

  const { data, isLoading } = useAccounts({ page, size: PAGE_SIZE, accountType: activeType || undefined })
  const accounts = data?.data ?? []
  const total = data?.meta?.total ?? 0
  const totalPages = Math.ceil(total / PAGE_SIZE)

  return (
    <div>
      <PageHeader
        title="Accounts"
        subtitle={`${total.toLocaleString()} total`}
        actions={
          <button onClick={() => setShowCreate(true)}
            className="px-4 py-2 rounded-lg text-sm font-medium text-white"
            style={{ background: 'var(--color-primary)' }}>
            + Open Account
          </button>
        }
      />

      {/* Type filter tabs */}
      <div className="flex gap-1 mb-6 rounded-lg p-1 w-fit" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        {TYPE_TABS.map(t => (
          <button key={t.value} onClick={() => { setActiveType(t.value); setPage(0) }}
            className="px-3 py-1 rounded-md text-xs font-medium transition-colors"
            style={{ background: activeType === t.value ? 'var(--color-primary)' : 'transparent', color: activeType === t.value ? '#fff' : 'var(--color-muted)' }}>
            {t.label}
          </button>
        ))}
      </div>

      <div className="rounded-xl" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <DataTable columns={columns} data={accounts} loading={isLoading}
          emptyMessage="No accounts match your filters" getRowKey={r => r.id} />
        {totalPages > 1 && <Pagination page={page} totalPages={totalPages} total={total} onChange={setPage} />}
      </div>

      <OpenAccountModal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onCreated={id => { setShowCreate(false); navigate(`/accounts/${id}`) }}
      />
    </div>
  )
}

function Pagination({ page, totalPages, total, onChange }: { page: number; totalPages: number; total: number; onChange: (p: number) => void }) {
  return (
    <div className="flex items-center justify-between px-6 py-3" style={{ borderTop: '1px solid var(--color-border)' }}>
      <span className="text-xs tabular-nums" style={{ color: 'var(--color-muted)' }}>Page {page + 1} of {totalPages} · {total.toLocaleString()} results</span>
      <div className="flex gap-2">
        <button disabled={page === 0} onClick={() => onChange(page - 1)} className="px-3 py-1 text-xs rounded-md disabled:opacity-40" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>← Prev</button>
        <button disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)} className="px-3 py-1 text-xs rounded-md disabled:opacity-40" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Next →</button>
      </div>
    </div>
  )
}

function OpenAccountModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (id: string) => void }) {
  const create = useCreateAccount()
  const [form, setForm] = useState({ customerId: '', productId: '', accountType: 'SAVINGS' as AccountType, currencyCode: 'USD' })
  const [error, setError] = useState('')

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault(); setError('')
    try {
      const res = await create.mutateAsync(form)
      const id = (res.data as { data: { id: string } }).data?.id
      if (id) onCreated(id)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to open account') }
  }

  return (
    <Modal open={open} onClose={onClose} title="Open Account" size="md"
      footer={
        <div className="flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button form="open-account-form" type="submit" disabled={create.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
            {create.isPending ? 'Opening…' : 'Open Account'}
          </button>
        </div>
      }>
      <form id="open-account-form" onSubmit={handleSubmit} className="space-y-4 p-6">
        {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
        <Field label="Customer ID" value={form.customerId} onChange={e => setForm(f => ({ ...f, customerId: e.target.value }))} required />
        <Field label="Product ID" value={form.productId} onChange={e => setForm(f => ({ ...f, productId: e.target.value }))} required />
        <div>
          <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Account Type</label>
          <select value={form.accountType} onChange={e => setForm(f => ({ ...f, accountType: e.target.value as AccountType }))}
            className="w-full px-3 py-2 rounded-lg text-sm outline-none"
            style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
            <option value="SAVINGS">Savings</option>
            <option value="CHECKING">Checking</option>
            <option value="FIXED_DEPOSIT">Fixed Deposit</option>
          </select>
        </div>
        <Field label="Currency Code (e.g. USD)" value={form.currencyCode} onChange={e => setForm(f => ({ ...f, currencyCode: e.target.value }))} required />
      </form>
    </Modal>
  )
}

function Field({ label, ...props }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <input {...props} className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
    </div>
  )
}
