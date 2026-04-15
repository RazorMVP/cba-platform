// web-react/src/app/features/products/fixed-deposits/FixedDepositDetailPage.tsx
import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useFixedDepositProduct, useCreateFixedDepositProduct, useUpdateFixedDepositProduct, useDeleteFixedDepositProduct } from '../api/useFixedDeposits'
import type { CreateFixedDepositProductRequest } from '../api/types'

export default function FixedDepositDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isNew = id === 'new'

  const { data, isLoading } = useFixedDepositProduct(id!)
  const product = data?.data

  const create = useCreateFixedDepositProduct()
  const update = useUpdateFixedDepositProduct(id!)
  const del = useDeleteFixedDepositProduct()

  const [editMode, setEditMode] = useState(isNew)
  const [form, setForm] = useState<CreateFixedDepositProductRequest>(blankForm())
  const [tab, setTab] = useState<'core' | 'rates' | 'term' | 'penalty'>('core')
  const [showDelete, setShowDelete] = useState(false)
  const [error, setError] = useState('')

  function blankForm(): CreateFixedDepositProductRequest {
    return { name: '', shortName: '', description: '', currencyCode: 'USD', nominalAnnualInterestRate: 0, minDepositAmount: 0, minDepositTerm: 1 }
  }

  function enterEditMode() {
    if (!product) return
    setForm({
      name: product.name, shortName: product.shortName, description: product.description ?? '',
      currencyCode: product.currencyCode, nominalAnnualInterestRate: product.nominalAnnualInterestRate,
      minDepositAmount: product.minDepositAmount, maxDepositAmount: product.maxDepositAmount,
      minDepositTerm: product.minDepositTerm, maxDepositTerm: product.maxDepositTerm,
      penaltyInterestRate: product.penaltyInterestRate,
    })
    setEditMode(true); setError('')
  }

  async function save() {
    setError('')
    try {
      if (isNew) {
        const res = await create.mutateAsync(form)
        const newId = (res as { data: { id: string } }).data?.id
        if (newId) navigate(`../${newId}`, { relative: 'path' })
      } else {
        await update.mutateAsync(form)
        setEditMode(false)
      }
    } catch (err) { setError(err instanceof Error ? err.message : 'Save failed') }
  }

  async function handleDelete() {
    try { await del.mutateAsync(id!); navigate('/products/fixed-deposits') }
    catch (err) { setError(err instanceof Error ? err.message : 'Delete failed') }
  }

  if (!isNew && isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
  if (!isNew && !product) return <div className="p-8 text-sm" style={{ color: 'var(--color-error)' }}>Product not found.</div>

  const isSaving = create.isPending || update.isPending
  const f = form

  if (isNew || editMode) {
    return (
      <div>
        <PageHeader title={isNew ? 'New Fixed Deposit Product' : `Edit: ${product?.name}`}
          actions={<Btn label="Cancel" onClick={() => isNew ? navigate(-1) : setEditMode(false)} secondary />} />
        {error && <ErrBox msg={error} />}
        <div className="rounded-xl p-6 space-y-4" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Product Name" value={f.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} required />
            <Field label="Short Name (4 chars)" value={f.shortName} maxLength={4} onChange={e => setForm(p => ({ ...p, shortName: e.target.value.toUpperCase() }))} required />
          </div>
          <Field label="Description (optional)" value={f.description ?? ''} onChange={e => setForm(p => ({ ...p, description: e.target.value }))} />
          <div className="grid grid-cols-2 gap-4">
            <Field label="Currency Code" value={f.currencyCode} onChange={e => setForm(p => ({ ...p, currencyCode: e.target.value.toUpperCase() }))} required />
            <Field label="Annual Interest Rate (%)" type="number" min="0" step="0.01" value={String(f.nominalAnnualInterestRate)} onChange={e => setForm(p => ({ ...p, nominalAnnualInterestRate: parseFloat(e.target.value) || 0 }))} required />
          </div>
          <p className="text-xs font-semibold uppercase tracking-wider pt-2" style={{ color: 'var(--color-muted)' }}>Deposit Amount</p>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Min Deposit" type="number" min="0" step="0.01" value={String(f.minDepositAmount)} onChange={e => setForm(p => ({ ...p, minDepositAmount: parseFloat(e.target.value) || 0 }))} required />
            <Field label="Max Deposit (optional)" type="number" min="0" step="0.01" value={String(f.maxDepositAmount ?? '')} onChange={e => setForm(p => ({ ...p, maxDepositAmount: parseFloat(e.target.value) || undefined }))} />
          </div>
          <p className="text-xs font-semibold uppercase tracking-wider pt-2" style={{ color: 'var(--color-muted)' }}>Term (months)</p>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Min Term" type="number" min="1" value={String(f.minDepositTerm)} onChange={e => setForm(p => ({ ...p, minDepositTerm: parseInt(e.target.value) || 1 }))} required />
            <Field label="Max Term (optional)" type="number" min="1" value={String(f.maxDepositTerm ?? '')} onChange={e => setForm(p => ({ ...p, maxDepositTerm: parseInt(e.target.value) || undefined }))} />
          </div>
          <Field label="Penalty Rate on Premature Close (%)" type="number" min="0" step="0.01" value={String(f.penaltyInterestRate ?? '')} onChange={e => setForm(p => ({ ...p, penaltyInterestRate: parseFloat(e.target.value) || undefined }))} />
        </div>
        <EditActions onSave={save} onCancel={() => isNew ? navigate(-1) : setEditMode(false)} saving={isSaving} />
      </div>
    )
  }

  return (
    <div>
      <PageHeader title={product!.name} subtitle={product!.shortName}
        actions={
          <div className="flex gap-2">
            <Btn label="Edit" onClick={enterEditMode} />
            <Btn label="Delete" onClick={() => setShowDelete(true)} danger />
          </div>
        }
      />
      {error && <ErrBox msg={error} />}

      <div className="flex items-center gap-3 mb-6">
        <StatusBadge label={product!.active ? 'Active' : 'Inactive'} variant={product!.active ? 'success' : 'neutral'} />
        <span className="text-xs font-mono px-2 py-0.5 rounded" style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)' }}>Fixed Deposit</span>
        <span className="text-xs" style={{ color: 'var(--color-muted)' }}>Created {new Date(product!.createdAt).toLocaleDateString()}</span>
      </div>

      <div className="flex gap-1 mb-6 border-b" style={{ borderColor: 'var(--color-border)' }}>
        {(['core', 'rates', 'term', 'penalty'] as const).map(t => (
          <button key={t} onClick={() => setTab(t)}
            className="px-4 py-2 text-sm font-medium capitalize transition-colors border-b-2 -mb-px"
            style={{ borderColor: tab === t ? 'var(--color-primary)' : 'transparent', color: tab === t ? 'var(--color-primary)' : 'var(--color-muted)' }}>
            {t.charAt(0).toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      {tab === 'core' && (
        <Grid>
          <Row label="Name" value={product!.name} />
          <Row label="Short Name" value={<span className="font-mono">{product!.shortName}</span>} />
          <Row label="Currency" value={product!.currencyCode} />
          <Row label="Description" value={product!.description || '—'} />
        </Grid>
      )}

      {tab === 'rates' && (
        <Grid>
          <Row label="Nominal Annual Rate" value={`${product!.nominalAnnualInterestRate}% p.a.`} />
        </Grid>
      )}

      {tab === 'term' && (
        <Grid>
          <Row label="Min Deposit Amount" value={product!.minDepositAmount.toLocaleString()} />
          <Row label="Max Deposit Amount" value={product!.maxDepositAmount?.toLocaleString() ?? 'No limit'} />
          <Row label="Min Term" value={`${product!.minDepositTerm} month${product!.minDepositTerm !== 1 ? 's' : ''}`} />
          <Row label="Max Term" value={product!.maxDepositTerm ? `${product!.maxDepositTerm} months` : 'No limit'} />
        </Grid>
      )}

      {tab === 'penalty' && (
        <Grid>
          <Row label="Premature Close Penalty Rate" value={product!.penaltyInterestRate !== undefined ? `${product!.penaltyInterestRate}%` : 'None'} />
        </Grid>
      )}

      <Modal open={showDelete} onClose={() => setShowDelete(false)} title="Delete Fixed Deposit Product" size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowDelete(false)} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={handleDelete} disabled={del.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-error)' }}>
              {del.isPending ? 'Deleting…' : 'Delete'}
            </button>
          </div>
        }>
        <div className="p-6">
          <p className="text-sm" style={{ color: 'var(--color-text)' }}>Delete <strong>{product!.name}</strong>? This cannot be undone.</p>
        </div>
      </Modal>
    </div>
  )
}

function Grid({ children }: { children: React.ReactNode }) {
  return <div className="grid grid-cols-2 gap-x-8 gap-y-4 rounded-xl p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>{children}</div>
}
function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return <div><p className="text-xs font-medium mb-0.5" style={{ color: 'var(--color-muted)' }}>{label}</p><p className="text-sm" style={{ color: 'var(--color-text)' }}>{value}</p></div>
}
function Btn({ label, onClick, secondary, danger }: { label: string; onClick: () => void; secondary?: boolean; danger?: boolean }) {
  return (
    <button onClick={onClick} className="px-4 py-2 rounded-lg text-sm font-medium"
      style={{ background: danger ? 'var(--color-error)' : secondary ? 'transparent' : 'var(--color-primary)', color: secondary ? 'var(--color-text)' : '#fff', border: secondary ? '1px solid var(--color-border)' : 'none' }}>
      {label}
    </button>
  )
}
function EditActions({ onSave, onCancel, saving }: { onSave: () => void; onCancel: () => void; saving: boolean }) {
  return (
    <div className="flex justify-end gap-3 mt-6">
      <button onClick={onCancel} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
      <button onClick={onSave} disabled={saving} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
        {saving ? 'Saving…' : 'Save Changes'}
      </button>
    </div>
  )
}
function ErrBox({ msg }: { msg: string }) {
  return <p className="text-sm p-3 rounded-lg mb-4" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{msg}</p>
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
