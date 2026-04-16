// web-react/src/app/features/products/deposit-products/DepositProductDetailPage.tsx
import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useDepositProduct, useCreateDepositProduct, useUpdateDepositProduct, useDeleteDepositProduct } from '../api/useDepositProducts'
import type { CreateDepositProductRequest, DepositAccountType, InterestCompoundingPeriodType, RepaymentFrequencyType } from '../api/types'

const COMPOUNDING_OPTS = [
  { label: 'Daily', value: 'DAILY' },
  { label: 'Monthly', value: 'MONTHLY' },
  { label: 'Quarterly', value: 'QUARTERLY' },
  { label: 'Annually', value: 'ANNUALLY' },
]

const FREQ_OPTS = [
  { label: 'Months', value: 'MONTHS' },
  { label: 'Weeks', value: 'WEEKS' },
  { label: 'Days', value: 'DAYS' },
]

export default function DepositProductDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isNew = id === 'new'

  const { data, isLoading } = useDepositProduct(id!)
  const product = data?.data

  const create = useCreateDepositProduct()
  const update = useUpdateDepositProduct(id!)
  const del = useDeleteDepositProduct()

  const [editMode, setEditMode] = useState(isNew)
  const [form, setForm] = useState<CreateDepositProductRequest>(blankForm())
  const [tab, setTab] = useState<'core' | 'interest' | 'overdraft' | 'charges'>('core')
  const [showDelete, setShowDelete] = useState(false)
  const [error, setError] = useState('')

  function blankForm(): CreateDepositProductRequest {
    return {
      name: '', shortName: '', description: '', currencyCode: 'USD',
      accountType: 'SAVINGS', minimumBalance: 0, minRequiredOpeningBalance: 0,
      nominalAnnualInterestRate: 0, interestCompoundingPeriodType: 'MONTHLY',
      interestPostingPeriodType: 'MONTHLY', withdrawalFeeForTransfers: false, allowOverdraft: false,
    }
  }

  function enterEditMode() {
    if (!product) return
    setForm({
      name: product.name,
      shortName: product.shortName,
      description: product.description ?? '',
      currencyCode: product.currencyCode,
      accountType: product.accountType,
      minimumBalance: product.minimumBalance ?? 0,
      minRequiredOpeningBalance: product.minRequiredOpeningBalance ?? 0,
      nominalAnnualInterestRate: product.nominalAnnualInterestRate,
      interestCompoundingPeriodType: product.interestCompoundingPeriodType,
      interestPostingPeriodType: product.interestPostingPeriodType,
      withdrawalFeeForTransfers: product.withdrawalFeeForTransfers,
      allowOverdraft: product.allowOverdraft,
      overdraftLimit: product.overdraftLimit,
      overdraftInterestRate: product.overdraftInterestRate,
    })
    setEditMode(true)
    setError('')
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
    try {
      await del.mutateAsync(id!)
      navigate('/products/deposit-products')
    } catch (err) { setError(err instanceof Error ? err.message : 'Delete failed') }
  }

  if (!isNew && isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
  if (!isNew && !product) return <div className="p-8 text-sm" style={{ color: 'var(--color-error)' }}>Product not found.</div>

  const isSaving = create.isPending || update.isPending

  if (isNew || editMode) {
    return (
      <div>
        <PageHeader title={isNew ? 'New Deposit Product' : `Edit: ${product?.name}`}
          actions={<ActionBtn label="Cancel" onClick={() => isNew ? navigate(-1) : setEditMode(false)} secondary />} />
        {error && <ErrBox msg={error} />}
        <div className="rounded-xl p-6 space-y-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <DepositProductFormFields form={form} setForm={setForm} />
        </div>
        <EditActions onSave={save} onCancel={() => isNew ? navigate(-1) : setEditMode(false)} saving={isSaving} />
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        title={product!.name}
        subtitle={`${product!.shortName}`}
        actions={
          <div className="flex gap-2">
            <ActionBtn label="Edit" onClick={enterEditMode} />
            <ActionBtn label="Delete" onClick={() => setShowDelete(true)} danger />
          </div>
        }
      />
      {error && <ErrBox msg={error} />}

      <div className="flex items-center gap-3 mb-6">
        <StatusBadge label={product!.active ? 'Active' : 'Inactive'} variant={product!.active ? 'success' : 'neutral'} />
        <span className="text-xs font-mono px-2 py-0.5 rounded" style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)' }}>{product!.accountType}</span>
        <span className="text-xs" style={{ color: 'var(--color-muted)' }}>Created {new Date(product!.createdAt).toLocaleDateString()}</span>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-6 border-b" style={{ borderColor: 'var(--color-border)' }}>
        {(['core', 'interest', 'overdraft', 'charges'] as const).map(t => (
          <button key={t} onClick={() => setTab(t)}
            className="px-4 py-2 text-sm font-medium capitalize transition-colors border-b-2 -mb-px"
            style={{ borderColor: tab === t ? 'var(--color-primary)' : 'transparent', color: tab === t ? 'var(--color-primary)' : 'var(--color-muted)' }}>
            {t === 'overdraft' ? 'Overdraft' : t.charAt(0).toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      {tab === 'core' && (
        <InfoGrid>
          <InfoRow label="Name" value={product!.name} />
          <InfoRow label="Short Name" value={<span className="font-mono">{product!.shortName}</span>} />
          <InfoRow label="Currency" value={product!.currencyCode} />
          <InfoRow label="Account Type" value={product!.accountType === 'SAVINGS' ? 'Savings' : 'Checking'} />
          <InfoRow label="Description" value={product!.description || '—'} />
          <InfoRow label="Min Balance" value={product!.minimumBalance?.toLocaleString() ?? '—'} />
          <InfoRow label="Min Opening Balance" value={product!.minRequiredOpeningBalance?.toLocaleString() ?? '—'} />
          <InfoRow label="Withdrawal Fee for Transfers" value={product!.withdrawalFeeForTransfers ? 'Yes' : 'No'} />
        </InfoGrid>
      )}

      {tab === 'interest' && (
        <InfoGrid>
          <InfoRow label="Nominal Annual Rate" value={`${product!.nominalAnnualInterestRate}% p.a.`} />
          <InfoRow label="Compounding Period" value={product!.interestCompoundingPeriodType} />
          <InfoRow label="Posting Period" value={product!.interestPostingPeriodType} />
          <InfoRow label="Days in Year" value={product!.daysInYearType ?? '—'} />
          {product!.lockinPeriodFrequency && (
            <InfoRow label="Lock-in Period" value={`${product!.lockinPeriodFrequency} ${product!.lockinPeriodFrequencyType ?? ''}`} />
          )}
        </InfoGrid>
      )}

      {tab === 'overdraft' && (
        <div>
          <div className="flex items-center gap-3 mb-4">
            <StatusBadge label={product!.allowOverdraft ? 'Overdraft Enabled' : 'No Overdraft'} variant={product!.allowOverdraft ? 'info' : 'neutral'} />
          </div>
          {product!.allowOverdraft && (
            <InfoGrid>
              <InfoRow label="Overdraft Limit" value={product!.overdraftLimit?.toLocaleString() ?? '—'} />
              <InfoRow label="Overdraft Interest Rate" value={product!.overdraftInterestRate !== undefined ? `${product!.overdraftInterestRate}%` : '—'} />
              <InfoRow label="Min for Interest Calc" value={product!.minOverdraftForInterestCalculation?.toLocaleString() ?? '—'} />
            </InfoGrid>
          )}
          {!product!.allowOverdraft && (
            <p className="text-sm" style={{ color: 'var(--color-muted)' }}>Overdraft is not enabled for this product.</p>
          )}
        </div>
      )}

      {tab === 'charges' && (
        <div>
          {(!product!.charges || product!.charges.length === 0) ? (
            <p className="text-sm" style={{ color: 'var(--color-muted)' }}>No charges configured.</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr style={{ borderBottom: '1px solid var(--color-border)' }}>
                  {['Name', 'Calculation Type', 'Amount', 'Currency'].map(h => (
                    <th key={h} className="text-left py-2 pr-4 text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {product!.charges!.map((c: import('../api/types').ChargeRef) => (
                  <tr key={c.id} style={{ borderBottom: '1px solid var(--color-border)' }}>
                    <td className="py-2 pr-4">{c.name}</td>
                    <td className="py-2 pr-4 text-xs" style={{ color: 'var(--color-muted)' }}>{c.chargeCalculationType}</td>
                    <td className="py-2 pr-4 tabular-nums">{c.amount.toLocaleString()}</td>
                    <td className="py-2 pr-4 font-mono text-xs">{c.currencyCode}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      <Modal open={showDelete} onClose={() => setShowDelete(false)} title="Delete Deposit Product" size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowDelete(false)} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={handleDelete} disabled={del.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-error)' }}>
              {del.isPending ? 'Deleting…' : 'Delete'}
            </button>
          </div>
        }>
        <div className="p-6">
          <p className="text-sm" style={{ color: 'var(--color-text)' }}>Are you sure you want to delete <strong>{product!.name}</strong>? This cannot be undone.</p>
        </div>
      </Modal>
    </div>
  )
}

// ── Sub-components ────────────────────────────────────────────────────────────

function DepositProductFormFields({ form, setForm }: { form: CreateDepositProductRequest; setForm: React.Dispatch<React.SetStateAction<CreateDepositProductRequest>> }) {
  return (
    <div className="space-y-4">
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
      <p className="text-xs font-semibold uppercase tracking-wider pt-2" style={{ color: 'var(--color-muted)' }}>Interest</p>
      <div className="grid grid-cols-3 gap-4">
        <Field label="Annual Rate (%)" type="number" min="0" step="0.01" value={String(form.nominalAnnualInterestRate)} onChange={e => setForm(f => ({ ...f, nominalAnnualInterestRate: parseFloat(e.target.value) || 0 }))} required />
        <Select label="Compounding Period" value={form.interestCompoundingPeriodType} onChange={v => setForm(f => ({ ...f, interestCompoundingPeriodType: v as InterestCompoundingPeriodType }))} options={COMPOUNDING_OPTS} />
        <Select label="Posting Period" value={form.interestPostingPeriodType} onChange={v => setForm(f => ({ ...f, interestPostingPeriodType: v as InterestCompoundingPeriodType }))} options={COMPOUNDING_OPTS} />
      </div>
      <p className="text-xs font-semibold uppercase tracking-wider pt-2" style={{ color: 'var(--color-muted)' }}>Lock-in</p>
      <div className="grid grid-cols-2 gap-4">
        <Field label="Lock-in Period" type="number" min="0" value={String((form as { lockinPeriodFrequency?: number }).lockinPeriodFrequency ?? '')} onChange={e => setForm(f => ({ ...f, lockinPeriodFrequency: parseInt(e.target.value) || undefined }))} />
        <Select label="Lock-in Frequency" value={(form as { lockinPeriodFrequencyType?: string }).lockinPeriodFrequencyType ?? 'MONTHS'} onChange={v => setForm(f => ({ ...f, lockinPeriodFrequencyType: v as RepaymentFrequencyType }))} options={FREQ_OPTS} />
      </div>
      <p className="text-xs font-semibold uppercase tracking-wider pt-2" style={{ color: 'var(--color-muted)' }}>Settings</p>
      <div className="flex items-center gap-6">
        <label className="flex items-center gap-2 text-sm cursor-pointer" style={{ color: 'var(--color-text)' }}>
          <input type="checkbox" checked={form.allowOverdraft ?? false} onChange={e => setForm(f => ({ ...f, allowOverdraft: e.target.checked }))} />
          Allow Overdraft
        </label>
        <label className="flex items-center gap-2 text-sm cursor-pointer" style={{ color: 'var(--color-text)' }}>
          <input type="checkbox" checked={form.withdrawalFeeForTransfers ?? false} onChange={e => setForm(f => ({ ...f, withdrawalFeeForTransfers: e.target.checked }))} />
          Withdrawal Fee for Transfers
        </label>
      </div>
      {form.allowOverdraft && (
        <div className="grid grid-cols-2 gap-4">
          <Field label="Overdraft Limit" type="number" min="0" step="0.01" value={String((form as { overdraftLimit?: number }).overdraftLimit ?? '')} onChange={e => setForm(f => ({ ...f, overdraftLimit: parseFloat(e.target.value) || undefined }))} />
          <Field label="Overdraft Interest Rate (%)" type="number" min="0" step="0.01" value={String((form as { overdraftInterestRate?: number }).overdraftInterestRate ?? '')} onChange={e => setForm(f => ({ ...f, overdraftInterestRate: parseFloat(e.target.value) || undefined }))} />
        </div>
      )}
    </div>
  )
}

function InfoGrid({ children }: { children: React.ReactNode }) {
  return <div className="grid grid-cols-2 gap-x-8 gap-y-4 rounded-xl p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>{children}</div>
}

function InfoRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs font-medium mb-0.5" style={{ color: 'var(--color-muted)' }}>{label}</p>
      <p className="text-sm" style={{ color: 'var(--color-text)' }}>{value}</p>
    </div>
  )
}

function ActionBtn({ label, onClick, secondary, danger }: { label: string; onClick: () => void; secondary?: boolean; danger?: boolean }) {
  return (
    <button onClick={onClick} className="px-4 py-2 rounded-lg text-sm font-medium transition-colors"
      style={{
        background: danger ? 'var(--color-error)' : secondary ? 'transparent' : 'var(--color-primary)',
        color: secondary ? 'var(--color-text)' : '#fff',
        border: secondary ? '1px solid var(--color-border)' : 'none',
      }}>
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
