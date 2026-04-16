// web-react/src/app/features/products/deposit-products/DepositProductsListPage.tsx
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { DataTable, type ColumnDef } from '@/shared/components/DataTable'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useDepositProducts, useCreateDepositProduct } from '../api/useDepositProducts'
import type { DepositProduct, CreateDepositProductRequest, DepositAccountType, InterestCompoundingPeriodType } from '../api/types'

const STATUS_TABS = [
  { label: 'All', value: '' },
  { label: 'Active', value: 'true' },
  { label: 'Inactive', value: 'false' },
]

const TYPE_TABS = [
  { label: 'All Types', value: '' },
  { label: 'Savings', value: 'SAVINGS' },
  { label: 'Checking', value: 'CHECKING' },
]

const columns: ColumnDef<DepositProduct>[] = [
  { key: 'name', header: 'Name', cell: r => <Link to={`/products/deposit-products/${r.id}`} className="font-medium hover:underline" style={{ color: 'var(--color-info)' }}>{r.name}</Link> },
  { key: 'short', header: 'Short Name', cell: r => <span className="font-mono text-xs">{r.shortName}</span> },
  { key: 'type', header: 'Account Type', cell: r => r.accountType === 'SAVINGS' ? 'Savings' : 'Checking' },
  { key: 'currency', header: 'Currency', cell: r => r.currencyCode },
  { key: 'rate', header: 'Interest Rate', numeric: true, cell: r => `${r.nominalAnnualInterestRate}% p.a.` },
  { key: 'overdraft', header: 'Overdraft', cell: r => r.allowOverdraft ? <StatusBadge label="Enabled" variant="info" /> : <StatusBadge label="None" variant="neutral" /> },
  { key: 'status', header: 'Status', cell: r => <StatusBadge label={r.active ? 'Active' : 'Inactive'} variant={r.active ? 'success' : 'neutral'} /> },
]

export default function DepositProductsListPage() {
  const navigate = useNavigate()
  const [activeStatus, setActiveStatus] = useState('')
  const [activeType, setActiveType] = useState('')
  const [search, setSearch] = useState('')
  const [showCreate, setShowCreate] = useState(false)

  const { data, isLoading } = useDepositProducts(activeStatus !== '' ? { active: activeStatus === 'true' } : undefined)
  const products = (data?.data ?? []).filter(p => {
    if (search && !p.name.toLowerCase().includes(search.toLowerCase())) return false
    if (activeType && p.accountType !== activeType) return false
    return true
  })

  return (
    <div>
      <PageHeader
        title="Deposit Products"
        subtitle={`${products.length} products`}
        actions={
          <button onClick={() => setShowCreate(true)} className="px-4 py-2 rounded-lg text-sm font-medium text-white" style={{ background: 'var(--color-primary)' }}>
            + New Deposit Product
          </button>
        }
      />

      <div className="flex flex-wrap items-center gap-4 mb-6">
        <input type="search" placeholder="Search products…" value={search} onChange={e => setSearch(e.target.value)}
          className="px-3 py-2 rounded-lg text-sm outline-none max-w-xs"
          style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
        <div className="flex gap-1 rounded-lg p-1" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          {TYPE_TABS.map(t => (
            <button key={t.value} onClick={() => setActiveType(t.value)}
              className="px-3 py-1 rounded-md text-xs font-medium transition-colors"
              style={{ background: activeType === t.value ? 'var(--color-primary)' : 'transparent', color: activeType === t.value ? '#fff' : 'var(--color-muted)' }}>
              {t.label}
            </button>
          ))}
        </div>
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
        <DataTable columns={columns} data={products} loading={isLoading} emptyMessage="No deposit products found" getRowKey={r => r.id} />
      </div>

      <CreateDepositProductModal open={showCreate} onClose={() => setShowCreate(false)}
        onCreated={id => { setShowCreate(false); navigate(`/products/deposit-products/${id}`) }} />
    </div>
  )
}

const BLANK: CreateDepositProductRequest = {
  name: '', shortName: '', description: '', currencyCode: 'USD',
  accountType: 'SAVINGS', minimumBalance: 0, minRequiredOpeningBalance: 0,
  nominalAnnualInterestRate: 0, interestCompoundingPeriodType: 'MONTHLY',
  interestPostingPeriodType: 'MONTHLY', withdrawalFeeForTransfers: false, allowOverdraft: false,
}

function CreateDepositProductModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (id: string) => void }) {
  const create = useCreateDepositProduct()
  const [form, setForm] = useState<CreateDepositProductRequest>(BLANK)
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
    <Modal open={open} onClose={onClose} title="New Deposit Product" size="md"
      footer={
        <div className="flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button form="create-dp-form" type="submit" disabled={create.isPending}
            className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
            {create.isPending ? 'Creating…' : 'Create Product'}
          </button>
        </div>
      }>
      <form id="create-dp-form" onSubmit={handleSubmit} className="p-6 space-y-4">
        {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
        <div className="grid grid-cols-2 gap-4">
          <Field label="Product Name" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} required />
          <Field label="Short Name (4 chars)" value={form.shortName} maxLength={4} onChange={e => setForm(f => ({ ...f, shortName: e.target.value.toUpperCase() }))} required />
        </div>
        <Field label="Description (optional)" value={form.description ?? ''} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
        <div className="grid grid-cols-2 gap-4">
          <Field label="Currency Code" value={form.currencyCode} onChange={e => setForm(f => ({ ...f, currencyCode: e.target.value.toUpperCase() }))} required />
          <Select label="Account Type" value={form.accountType} onChange={v => setForm(f => ({ ...f, accountType: v as DepositAccountType }))}
            options={[{ label: 'Savings', value: 'SAVINGS' }, { label: 'Checking', value: 'CHECKING' }]} />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Min Balance" type="number" min="0" step="0.01" value={String(form.minimumBalance ?? 0)} onChange={e => setForm(f => ({ ...f, minimumBalance: parseFloat(e.target.value) || 0 }))} />
          <Field label="Min Opening Balance" type="number" min="0" step="0.01" value={String(form.minRequiredOpeningBalance ?? 0)} onChange={e => setForm(f => ({ ...f, minRequiredOpeningBalance: parseFloat(e.target.value) || 0 }))} />
        </div>
        <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>Interest</p>
        <div className="grid grid-cols-3 gap-4">
          <Field label="Annual Rate (%)" type="number" min="0" step="0.01" value={String(form.nominalAnnualInterestRate)} onChange={e => setForm(f => ({ ...f, nominalAnnualInterestRate: parseFloat(e.target.value) || 0 }))} required />
          <Select label="Compounding" value={form.interestCompoundingPeriodType} onChange={v => setForm(f => ({ ...f, interestCompoundingPeriodType: v as InterestCompoundingPeriodType }))}
            options={[{ label: 'Daily', value: 'DAILY' }, { label: 'Monthly', value: 'MONTHLY' }, { label: 'Quarterly', value: 'QUARTERLY' }, { label: 'Annually', value: 'ANNUALLY' }]} />
          <Select label="Posting Period" value={form.interestPostingPeriodType} onChange={v => setForm(f => ({ ...f, interestPostingPeriodType: v as InterestCompoundingPeriodType }))}
            options={[{ label: 'Daily', value: 'DAILY' }, { label: 'Monthly', value: 'MONTHLY' }, { label: 'Quarterly', value: 'QUARTERLY' }, { label: 'Annually', value: 'ANNUALLY' }]} />
        </div>
        <div className="flex items-center gap-6">
          <label className="flex items-center gap-2 text-sm cursor-pointer" style={{ color: 'var(--color-text)' }}>
            <input type="checkbox" checked={form.allowOverdraft ?? false} onChange={e => setForm(f => ({ ...f, allowOverdraft: e.target.checked }))} className="rounded" />
            Allow Overdraft
          </label>
          <label className="flex items-center gap-2 text-sm cursor-pointer" style={{ color: 'var(--color-text)' }}>
            <input type="checkbox" checked={form.withdrawalFeeForTransfers ?? false} onChange={e => setForm(f => ({ ...f, withdrawalFeeForTransfers: e.target.checked }))} className="rounded" />
            Withdrawal Fee for Transfers
          </label>
        </div>
        {form.allowOverdraft && (
          <div className="grid grid-cols-2 gap-4">
            <Field label="Overdraft Limit" type="number" min="0" step="0.01" value={String(form.overdraftLimit ?? '')} onChange={e => setForm(f => ({ ...f, overdraftLimit: parseFloat(e.target.value) || undefined }))} />
            <Field label="Overdraft Interest Rate (%)" type="number" min="0" step="0.01" value={String(form.overdraftInterestRate ?? '')} onChange={e => setForm(f => ({ ...f, overdraftInterestRate: parseFloat(e.target.value) || undefined }))} />
          </div>
        )}
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
