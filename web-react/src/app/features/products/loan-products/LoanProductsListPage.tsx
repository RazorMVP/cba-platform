// web-react/src/app/features/products/loan-products/LoanProductsListPage.tsx
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { DataTable, type ColumnDef } from '@/shared/components/DataTable'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useLoanProducts, useCreateLoanProduct } from '../api/useLoanProducts'
import type { LoanProduct, CreateLoanProductRequest, InterestType, AmortizationType, RepaymentFrequencyType, InterestRateFrequencyType } from '../api/types'

const STATUS_TABS = [
  { label: 'All', value: '' },
  { label: 'Active', value: 'true' },
  { label: 'Inactive', value: 'false' },
]

const columns: ColumnDef<LoanProduct>[] = [
  { key: 'name', header: 'Name', cell: r => <Link to={`/products/loan-products/${r.id}`} className="font-medium hover:underline" style={{ color: 'var(--color-info)' }}>{r.name}</Link> },
  { key: 'short', header: 'Short Name', cell: r => <span className="font-mono text-xs">{r.shortName}</span> },
  { key: 'currency', header: 'Currency', cell: r => r.currencyCode },
  { key: 'principal', header: 'Principal Range', numeric: true, cell: r => `${r.minPrincipalAmount.toLocaleString()} – ${r.maxPrincipalAmount.toLocaleString()}` },
  { key: 'rate', header: 'Interest Rate', numeric: true, cell: r => `${r.defaultInterestRatePerPeriod}% p.a.` },
  { key: 'status', header: 'Status', cell: r => <StatusBadge label={r.active ? 'Active' : 'Inactive'} variant={r.active ? 'success' : 'neutral'} /> },
]

export default function LoanProductsListPage() {
  const navigate = useNavigate()
  const [activeStatus, setActiveStatus] = useState('')
  const [search, setSearch] = useState('')
  const [showCreate, setShowCreate] = useState(false)

  const { data, isLoading } = useLoanProducts(activeStatus !== '' ? { active: activeStatus === 'true' } : undefined)
  const products = (data?.data ?? []).filter(p => !search || p.name.toLowerCase().includes(search.toLowerCase()))

  return (
    <div>
      <PageHeader
        title="Loan Products"
        subtitle={`${products.length} products`}
        actions={
          <button onClick={() => setShowCreate(true)} className="px-4 py-2 rounded-lg text-sm font-medium text-white" style={{ background: 'var(--color-primary)' }}>
            + New Loan Product
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
        <DataTable columns={columns} data={products} loading={isLoading} emptyMessage="No loan products found" getRowKey={r => r.id} />
      </div>

      <CreateLoanProductModal open={showCreate} onClose={() => setShowCreate(false)}
        onCreated={id => { setShowCreate(false); navigate(`/products/loan-products/${id}`) }} />
    </div>
  )
}

const BLANK: CreateLoanProductRequest = {
  name: '', shortName: '', description: '', currencyCode: 'USD',
  minPrincipalAmount: 0, defaultPrincipalAmount: 0, maxPrincipalAmount: 0,
  defaultInterestRatePerPeriod: 0, interestRateFrequencyType: 'PER_YEAR',
  interestType: 'DECLINING_BALANCE', amortizationType: 'EQUAL_INSTALLMENTS',
  repaymentEvery: 1, repaymentFrequencyType: 'MONTHS', numberOfRepayments: 12,
}

function CreateLoanProductModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (id: string) => void }) {
  const create = useCreateLoanProduct()
  const [form, setForm] = useState<CreateLoanProductRequest>(BLANK)
  const [error, setError] = useState('')

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault(); setError('')
    try {
      const res = await create.mutateAsync(form)
      const id = (res as { data: { id: string } }).data?.id
      if (id) onCreated(id)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to create') }
  }

  function field(key: keyof CreateLoanProductRequest) {
    return (val: string) => setForm(f => ({ ...f, [key]: val }))
  }

  return (
    <Modal open={open} onClose={onClose} title="New Loan Product" size="md"
      footer={
        <div className="flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button form="create-lp-form" type="submit" disabled={create.isPending}
            className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
            {create.isPending ? 'Creating…' : 'Create Product'}
          </button>
        </div>
      }>
      <form id="create-lp-form" onSubmit={handleSubmit} className="p-6 space-y-4">
        {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
        <div className="grid grid-cols-2 gap-4">
          <Field label="Product Name" value={form.name} onChange={e => field('name')(e.target.value)} required />
          <Field label="Short Name (4 chars)" value={form.shortName} maxLength={4} onChange={e => field('shortName')(e.target.value.toUpperCase())} required />
        </div>
        <Field label="Description (optional)" value={form.description ?? ''} onChange={e => field('description')(e.target.value)} />
        <div className="grid grid-cols-2 gap-4">
          <Field label="Currency Code" value={form.currencyCode} onChange={e => field('currencyCode')(e.target.value.toUpperCase())} required />
          <Select label="Interest Rate Frequency" value={form.interestRateFrequencyType} onChange={v => setForm(f => ({ ...f, interestRateFrequencyType: v as InterestRateFrequencyType }))}
            options={[{ label: 'Per Year', value: 'PER_YEAR' }, { label: 'Per Month', value: 'PER_MONTH' }, { label: 'Per Week', value: 'PER_WEEK' }]} />
        </div>
        <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>Principal</p>
        <div className="grid grid-cols-3 gap-4">
          <Field label="Min" type="number" min="0" value={String(form.minPrincipalAmount)} onChange={e => setForm(f => ({ ...f, minPrincipalAmount: parseFloat(e.target.value) || 0 }))} required />
          <Field label="Default" type="number" min="0" value={String(form.defaultPrincipalAmount)} onChange={e => setForm(f => ({ ...f, defaultPrincipalAmount: parseFloat(e.target.value) || 0 }))} required />
          <Field label="Max" type="number" min="0" value={String(form.maxPrincipalAmount)} onChange={e => setForm(f => ({ ...f, maxPrincipalAmount: parseFloat(e.target.value) || 0 }))} required />
        </div>
        <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>Interest Rate (%)</p>
        <Field label="Default Interest Rate" type="number" min="0" step="0.01" value={String(form.defaultInterestRatePerPeriod)} onChange={e => setForm(f => ({ ...f, defaultInterestRatePerPeriod: parseFloat(e.target.value) || 0 }))} required />
        <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>Repayment</p>
        <div className="grid grid-cols-3 gap-4">
          <Field label="Repay Every" type="number" min="1" value={String(form.repaymentEvery)} onChange={e => setForm(f => ({ ...f, repaymentEvery: parseInt(e.target.value) || 1 }))} required />
          <Select label="Frequency" value={form.repaymentFrequencyType} onChange={v => setForm(f => ({ ...f, repaymentFrequencyType: v as RepaymentFrequencyType }))}
            options={[{ label: 'Months', value: 'MONTHS' }, { label: 'Weeks', value: 'WEEKS' }, { label: 'Days', value: 'DAYS' }]} />
          <Field label="# of Repayments" type="number" min="1" value={String(form.numberOfRepayments)} onChange={e => setForm(f => ({ ...f, numberOfRepayments: parseInt(e.target.value) || 1 }))} required />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <Select label="Interest Type" value={form.interestType} onChange={v => setForm(f => ({ ...f, interestType: v as InterestType }))}
            options={[{ label: 'Declining Balance', value: 'DECLINING_BALANCE' }, { label: 'Flat', value: 'FLAT' }]} />
          <Select label="Amortization" value={form.amortizationType} onChange={v => setForm(f => ({ ...f, amortizationType: v as AmortizationType }))}
            options={[{ label: 'Equal Installments', value: 'EQUAL_INSTALLMENTS' }, { label: 'Equal Principal', value: 'EQUAL_PRINCIPAL' }]} />
        </div>
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
