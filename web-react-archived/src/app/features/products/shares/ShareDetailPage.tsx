// web-react/src/app/features/products/shares/ShareDetailPage.tsx
import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useShareProduct, useCreateShareProduct, useUpdateShareProduct, useDeleteShareProduct } from '../api/useShares'
import type { CreateShareProductRequest, RepaymentFrequencyType } from '../api/types'

export default function ShareDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isNew = id === 'new'

  const { data, isLoading } = useShareProduct(id!)
  const product = data?.data

  const create = useCreateShareProduct()
  const update = useUpdateShareProduct(id!)
  const del = useDeleteShareProduct()

  const [editMode, setEditMode] = useState(isNew)
  const [form, setForm] = useState<CreateShareProductRequest>(blank())
  const [tab, setTab] = useState<'core' | 'shares' | 'lockin'>('core')
  const [showDelete, setShowDelete] = useState(false)
  const [error, setError] = useState('')

  function blank(): CreateShareProductRequest {
    return { name: '', shortName: '', description: '', currencyCode: 'USD', unitPrice: 0, minimumShares: 1 }
  }

  function enterEditMode() {
    if (!product) return
    setForm({
      name: product.name, shortName: product.shortName, description: product.description ?? '',
      currencyCode: product.currencyCode, unitPrice: product.unitPrice,
      minimumShares: product.minimumShares, maximumShares: product.maximumShares,
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
    try { await del.mutateAsync(id!); navigate('/products/shares') }
    catch (err) { setError(err instanceof Error ? err.message : 'Delete failed') }
  }

  if (!isNew && isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
  if (!isNew && !product) return <div className="p-8 text-sm" style={{ color: 'var(--color-error)' }}>Product not found.</div>

  const isSaving = create.isPending || update.isPending
  const f = form

  if (isNew || editMode) {
    return (
      <div>
        <PageHeader title={isNew ? 'New Share Product' : `Edit: ${product?.name}`}
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
            <Field label="Unit Price per Share" type="number" min="0" step="0.01" value={String(f.unitPrice)} onChange={e => setForm(p => ({ ...p, unitPrice: parseFloat(e.target.value) || 0 }))} required />
          </div>
          <p className="text-xs font-semibold uppercase tracking-wider pt-2" style={{ color: 'var(--color-muted)' }}>Share Limits per Member</p>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Minimum Shares" type="number" min="1" value={String(f.minimumShares)} onChange={e => setForm(p => ({ ...p, minimumShares: parseInt(e.target.value) || 1 }))} required />
            <Field label="Maximum Shares (optional)" type="number" min="1" value={String(f.maximumShares ?? '')} onChange={e => setForm(p => ({ ...p, maximumShares: parseInt(e.target.value) || undefined }))} />
          </div>
          <p className="text-xs font-semibold uppercase tracking-wider pt-2" style={{ color: 'var(--color-muted)' }}>Lock-in (optional)</p>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Lock-in Period" type="number" min="0" value={String((f as { lockinPeriodFrequency?: number }).lockinPeriodFrequency ?? '')} onChange={e => setForm(p => ({ ...p, lockinPeriodFrequency: parseInt(e.target.value) || undefined }))} />
            <Select label="Lock-in Frequency" value={(f as { lockinPeriodFrequencyType?: string }).lockinPeriodFrequencyType ?? 'MONTHS'} onChange={v => setForm(p => ({ ...p, lockinPeriodFrequencyType: v as RepaymentFrequencyType }))}
              options={[{ label: 'Months', value: 'MONTHS' }, { label: 'Weeks', value: 'WEEKS' }, { label: 'Days', value: 'DAYS' }]} />
          </div>
        </div>
        <EditActions onSave={save} onCancel={() => isNew ? navigate(-1) : setEditMode(false)} saving={isSaving} />
      </div>
    )
  }

  return (
    <div>
      <PageHeader title={product!.name} subtitle={product!.shortName}
        actions={<div className="flex gap-2"><Btn label="Edit" onClick={enterEditMode} /><Btn label="Delete" onClick={() => setShowDelete(true)} danger /></div>}
      />
      {error && <ErrBox msg={error} />}

      <div className="flex items-center gap-3 mb-6">
        <StatusBadge label={product!.active ? 'Active' : 'Inactive'} variant={product!.active ? 'success' : 'neutral'} />
        <span className="text-xs font-mono px-2 py-0.5 rounded" style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)' }}>Share Product</span>
        <span className="text-xs" style={{ color: 'var(--color-muted)' }}>Created {new Date(product!.createdAt).toLocaleDateString()}</span>
      </div>

      <div className="flex gap-1 mb-6 border-b" style={{ borderColor: 'var(--color-border)' }}>
        {(['core', 'shares', 'lockin'] as const).map(t => (
          <button key={t} onClick={() => setTab(t)}
            className="px-4 py-2 text-sm font-medium capitalize transition-colors border-b-2 -mb-px"
            style={{ borderColor: tab === t ? 'var(--color-primary)' : 'transparent', color: tab === t ? 'var(--color-primary)' : 'var(--color-muted)' }}>
            {t === 'lockin' ? 'Lock-in' : t.charAt(0).toUpperCase() + t.slice(1)}
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

      {tab === 'shares' && (
        <Grid>
          <Row label="Unit Price" value={product!.unitPrice.toLocaleString(undefined, { minimumFractionDigits: 2 })} />
          <Row label="Total Shares Issued" value={product!.sharesIssued.toLocaleString()} />
          <Row label="Min Shares per Member" value={product!.minimumShares.toLocaleString()} />
          <Row label="Max Shares per Member" value={product!.maximumShares?.toLocaleString() ?? 'No limit'} />
          {product!.dividendPolicy && <Row label="Dividend Policy" value={product!.dividendPolicy} />}
        </Grid>
      )}

      {tab === 'lockin' && (
        <Grid>
          {product!.lockinPeriodFrequency ? (
            <Row label="Lock-in Period" value={`${product!.lockinPeriodFrequency} ${product!.lockinPeriodFrequencyType ?? ''}`} />
          ) : (
            <Row label="Lock-in Period" value="None" />
          )}
        </Grid>
      )}

      <Modal open={showDelete} onClose={() => setShowDelete(false)} title="Delete Share Product" size="sm"
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
