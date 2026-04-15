// web-react/src/app/features/products/recurring-deposits/RecurringDepositsListPage.tsx
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { DataTable, type ColumnDef } from '@/shared/components/DataTable'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useRecurringDepositProducts, useCreateRecurringDepositProduct } from '../api/useRecurringDeposits'
import type { RecurringDepositProduct, CreateRecurringDepositProductRequest, RepaymentFrequencyType } from '../api/types'

const STATUS_TABS = [
  { label: 'All', value: '' },
  { label: 'Active', value: 'true' },
  { label: 'Inactive', value: 'false' },
]

const FREQ_LABEL: Record<string, string> = { DAYS: 'day(s)', WEEKS: 'week(s)', MONTHS: 'month(s)' }

const columns: ColumnDef<RecurringDepositProduct>[] = [
  { key: 'name', header: 'Name', cell: r => <Link to={`/products/recurring-deposits/${r.id}`} className="font-medium hover:underline" style={{ color: 'var(--color-info)' }}>{r.name}</Link> },
  { key: 'short', header: 'Short Name', cell: r => <span className="font-mono text-xs">{r.shortName}</span> },
  { key: 'currency', header: 'Currency', cell: r => r.currencyCode },
  { key: 'rate', header: 'Interest Rate', numeric: true, cell: r => `${r.nominalAnnualInterestRate}% p.a.` },
  { key: 'deposit', header: 'Min Deposit', numeric: true, cell: r => r.minDepositAmount.toLocaleString() },
  { key: 'freq', header: 'Deposit Frequency', cell: r => `Every ${r.recurringDepositFrequency} ${FREQ_LABEL[r.recurringDepositFrequencyType] ?? r.recurringDepositFrequencyType}` },
  { key: 'status', header: 'Status', cell: r => <StatusBadge label={r.active ? 'Active' : 'Inactive'} variant={r.active ? 'success' : 'neutral'} /> },
]

export default function RecurringDepositsListPage() {
  const navigate = useNavigate()
  const [activeStatus, setActiveStatus] = useState('')
  const [search, setSearch] = useState('')
  const [showCreate, setShowCreate] = useState(false)

  const { data, isLoading } = useRecurringDepositProducts(activeStatus !== '' ? { active: activeStatus === 'true' } : undefined)
  const products = (data?.data ?? []).filter(p => !search || p.name.toLowerCase().includes(search.toLowerCase()))

  return (
    <div>
      <PageHeader
        title="Recurring Deposit Products"
        subtitle={`${products.length} products`}
        actions={
          <button onClick={() => setShowCreate(true)} className="px-4 py-2 rounded-lg text-sm font-medium text-white" style={{ background: 'var(--color-primary)' }}>
            + New Recurring Deposit
          </button>
        }
      />

      <div className="flex items-center gap-4 mb-6">
        <input type="search" placeholder="Search products…" value={search} onChange={e => setSearch(e.target.value)}
          className="px-3 py-2 rounded-lg text-sm outline-none max-w-xs"
          style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
        <div className="flex gap-1 rounded-lg p-1" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          {STATUS_TABS.map(t => (
            <button key={t.value} onClick={() => setActiveStatus(t.value)}
              className="px-3 py-1 rounded-md text-xs font-medium transition-colors"
              style={{ background: activeStatus === t.value ? 'var(--color-primary)' : 'transparent', color: activeStatus === t.value ? '#fff' : 'var(--color-muted)' }}>
              {t.label}
            </button>
          ))}
        </div>
      </div>

      <div className="rounded-xl" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <DataTable columns={columns} data={products} loading={isLoading} emptyMessage="No recurring deposit products found" getRowKey={r => r.id} />
      </div>

      <CreateRecurringDepositModal open={showCreate} onClose={() => setShowCreate(false)}
        onCreated={id => { setShowCreate(false); navigate(`/products/recurring-deposits/${id}`) }} />
    </div>
  )
}

const BLANK: CreateRecurringDepositProductRequest = {
  name: '', shortName: '', description: '', currencyCode: 'USD',
  nominalAnnualInterestRate: 0, minDepositAmount: 0,
  recurringDepositFrequency: 1, recurringDepositFrequencyType: 'MONTHS',
}

function CreateRecurringDepositModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (id: string) => void }) {
  const create = useCreateRecurringDepositProduct()
  const [form, setForm] = useState<CreateRecurringDepositProductRequest>(BLANK)
  const [error, setError] = useState('')

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault(); setError('')
    try {
      const res = await create.mutateAsync(form)
      const id = (res as { data: { id: string } }).data?.id
      if (id) onCreated(id)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to create') }
  }

  return (
    <Modal open={open} onClose={onClose} title="New Recurring Deposit Product" size="md"
      footer={
        <div className="flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button form="create-rd-form" type="submit" disabled={create.isPending}
            className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
            {create.isPending ? 'Creating…' : 'Create Product'}
          </button>
        </div>
      }>
      <form id="create-rd-form" onSubmit={handleSubmit} className="p-6 space-y-4">
        {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
        <div className="grid grid-cols-2 gap-4">
          <Field label="Product Name" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} required />
          <Field label="Short Name (4 chars)" value={form.shortName} maxLength={4} onChange={e => setForm(f => ({ ...f, shortName: e.target.value.toUpperCase() }))} required />
        </div>
        <Field label="Description (optional)" value={form.description ?? ''} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
        <div className="grid grid-cols-2 gap-4">
          <Field label="Currency Code" value={form.currencyCode} onChange={e => setForm(f => ({ ...f, currencyCode: e.target.value.toUpperCase() }))} required />
          <Field label="Annual Interest Rate (%)" type="number" min="0" step="0.01" value={String(form.nominalAnnualInterestRate)} onChange={e => setForm(f => ({ ...f, nominalAnnualInterestRate: parseFloat(e.target.value) || 0 }))} required />
        </div>
        <Field label="Min Deposit Amount" type="number" min="0" step="0.01" value={String(form.minDepositAmount)} onChange={e => setForm(f => ({ ...f, minDepositAmount: parseFloat(e.target.value) || 0 }))} required />
        <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>Deposit Frequency</p>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Every" type="number" min="1" value={String(form.recurringDepositFrequency)} onChange={e => setForm(f => ({ ...f, recurringDepositFrequency: parseInt(e.target.value) || 1 }))} required />
          <Select label="Period" value={form.recurringDepositFrequencyType} onChange={v => setForm(f => ({ ...f, recurringDepositFrequencyType: v as RepaymentFrequencyType }))}
            options={[{ label: 'Months', value: 'MONTHS' }, { label: 'Weeks', value: 'WEEKS' }, { label: 'Days', value: 'DAYS' }]} />
        </div>
        <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>Term (optional)</p>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Min Term (months)" type="number" min="1" value={String(form.minDepositTerm ?? '')} onChange={e => setForm(f => ({ ...f, minDepositTerm: parseInt(e.target.value) || undefined }))} />
          <Field label="Max Term (months)" type="number" min="1" value={String(form.maxDepositTerm ?? '')} onChange={e => setForm(f => ({ ...f, maxDepositTerm: parseInt(e.target.value) || undefined }))} />
        </div>
        <Field label="Penalty Rate on Premature Close (%)" type="number" min="0" step="0.01" value={String(form.penaltyInterestRate ?? '')} onChange={e => setForm(f => ({ ...f, penaltyInterestRate: parseFloat(e.target.value) || undefined }))} />
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

function Select({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: { label: string; value: string }[] }) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <select value={value} onChange={e => onChange(e.target.value)}
        className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </div>
  )
}
