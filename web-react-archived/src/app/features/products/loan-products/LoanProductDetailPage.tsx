// web-react/src/app/features/products/loan-products/LoanProductDetailPage.tsx
import { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useLoanProduct, useCreateLoanProduct, useUpdateLoanProduct, useDeleteLoanProduct } from '../api/useLoanProducts'
import type { CreateLoanProductRequest, InterestType, AmortizationType, RepaymentFrequencyType, InterestRateFrequencyType } from '../api/types'

type Tab = 'core' | 'interest' | 'gl' | 'charges'

export default function LoanProductDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isNew = id === 'new'
  const [tab, setTab] = useState<Tab>('core')
  const [editMode, setEditMode] = useState(false)
  const [showDelete, setShowDelete] = useState(false)
  const [error, setError] = useState('')

  const { data: productData, isLoading } = useLoanProduct(id ?? '')
  const product = productData?.data

  const createMut = useCreateLoanProduct()
  const updateMut = useUpdateLoanProduct(id ?? '')
  const deleteMut = useDeleteLoanProduct()

  const blankForm: CreateLoanProductRequest = {
    name: '', shortName: '', description: '', currencyCode: 'USD',
    minPrincipalAmount: 0, defaultPrincipalAmount: 0, maxPrincipalAmount: 0,
    defaultInterestRatePerPeriod: 0, interestRateFrequencyType: 'PER_YEAR',
    interestType: 'DECLINING_BALANCE', amortizationType: 'EQUAL_INSTALLMENTS',
    repaymentEvery: 1, repaymentFrequencyType: 'MONTHS', numberOfRepayments: 12,
  }

  const [form, setForm] = useState<CreateLoanProductRequest>(blankForm)

  function enterEditMode() {
    if (!product) return
    setForm({
      name: product.name, shortName: product.shortName, description: product.description ?? '',
      currencyCode: product.currencyCode,
      minPrincipalAmount: product.minPrincipalAmount,
      defaultPrincipalAmount: product.defaultPrincipalAmount,
      maxPrincipalAmount: product.maxPrincipalAmount,
      defaultInterestRatePerPeriod: product.defaultInterestRatePerPeriod,
      minInterestRatePerPeriod: product.minInterestRatePerPeriod,
      maxInterestRatePerPeriod: product.maxInterestRatePerPeriod,
      interestRateFrequencyType: product.interestRateFrequencyType,
      interestType: product.interestType,
      amortizationType: product.amortizationType,
      repaymentEvery: product.repaymentEvery,
      repaymentFrequencyType: product.repaymentFrequencyType,
      numberOfRepayments: product.numberOfRepayments,
      graceOnPrincipalPayment: product.graceOnPrincipalPayment,
      graceOnInterestPayment: product.graceOnInterestPayment,
    })
    setEditMode(true)
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault(); setError('')
    try {
      const res = await createMut.mutateAsync(form)
      const newId = (res as { data: { id: string } }).data?.id
      if (newId) navigate(`/products/loan-products/${newId}`)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to create') }
  }

  async function handleSave() {
    setError('')
    try {
      await updateMut.mutateAsync(form)
      setEditMode(false)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to save') }
  }

  async function handleDelete() {
    try {
      await deleteMut.mutateAsync(id ?? '')
      navigate('/products/loan-products')
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to delete') }
  }

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  // ── New product form ───────────────────────────────────────────────────────
  if (isNew) {
    return (
      <div>
        <PageHeader title="New Loan Product" actions={<Link to="/products/loan-products" className="text-sm" style={{ color: 'var(--color-muted)' }}>← Back</Link>} />
        <form onSubmit={handleCreate} className="rounded-xl p-6 max-w-2xl space-y-4" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          {error && <ErrBox msg={error} />}
          <LoanProductFormFields form={form} setForm={setForm} />
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={() => navigate('/products/loan-products')} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button type="submit" disabled={createMut.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
              {createMut.isPending ? 'Creating…' : 'Create Product'}
            </button>
          </div>
        </form>
      </div>
    )
  }

  if (!product) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Product not found.</div>

  const tabs: { key: Tab; label: string }[] = [
    { key: 'core', label: 'Core' },
    { key: 'interest', label: 'Interest & Repayment' },
    { key: 'gl', label: 'GL Accounts' },
    { key: 'charges', label: `Charges (${product.charges?.length ?? 0})` },
  ]

  return (
    <div>
      <PageHeader
        title={product.name}
        subtitle={product.shortName}
        actions={
          <div className="flex items-center gap-3">
            <StatusBadge label={product.active ? 'Active' : 'Inactive'} variant={product.active ? 'success' : 'neutral'} />
            {!editMode && <ActionBtn onClick={enterEditMode}>Edit</ActionBtn>}
            {!editMode && <ActionBtn onClick={() => setShowDelete(true)} danger>Delete</ActionBtn>}
            <Link to="/products/loan-products" className="text-sm" style={{ color: 'var(--color-muted)' }}>← Back</Link>
          </div>
        }
      />

      {error && <ErrBox msg={error} />}

      {/* Tabs */}
      <div className="flex gap-1 mb-6 rounded-lg p-1 w-fit" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        {tabs.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className="px-4 py-1.5 rounded-md text-sm font-medium transition-colors"
            style={{ background: tab === t.key ? 'var(--color-primary)' : 'transparent', color: tab === t.key ? '#fff' : 'var(--color-muted)' }}>
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'core' && (
        <div className="rounded-xl p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          {editMode ? (
            <div className="space-y-4">
              <LoanProductCoreFields form={form} setForm={setForm} />
              <EditActions onSave={handleSave} onCancel={() => setEditMode(false)} isPending={updateMut.isPending} />
            </div>
          ) : (
            <dl className="grid grid-cols-2 gap-x-8 gap-y-4">
              <InfoRow label="Name" value={product.name} />
              <InfoRow label="Short Name" value={product.shortName} />
              <InfoRow label="Description" value={product.description ?? '—'} />
              <InfoRow label="Currency" value={product.currencyCode} />
              <InfoRow label="Min Principal" value={product.minPrincipalAmount.toLocaleString()} />
              <InfoRow label="Default Principal" value={product.defaultPrincipalAmount.toLocaleString()} />
              <InfoRow label="Max Principal" value={product.maxPrincipalAmount.toLocaleString()} />
              <InfoRow label="In Arrears Tolerance" value={product.inArrearsTolerance != null ? String(product.inArrearsTolerance) : '—'} />
              <InfoRow label="Created" value={new Date(product.createdAt).toLocaleDateString()} />
            </dl>
          )}
        </div>
      )}

      {tab === 'interest' && (
        <div className="rounded-xl p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          {editMode ? (
            <div className="space-y-4">
              <LoanProductInterestFields form={form} setForm={setForm} />
              <EditActions onSave={handleSave} onCancel={() => setEditMode(false)} isPending={updateMut.isPending} />
            </div>
          ) : (
            <dl className="grid grid-cols-2 gap-x-8 gap-y-4">
              <InfoRow label="Default Interest Rate" value={`${product.defaultInterestRatePerPeriod}%`} />
              <InfoRow label="Min Interest Rate" value={product.minInterestRatePerPeriod != null ? `${product.minInterestRatePerPeriod}%` : '—'} />
              <InfoRow label="Max Interest Rate" value={product.maxInterestRatePerPeriod != null ? `${product.maxInterestRatePerPeriod}%` : '—'} />
              <InfoRow label="Rate Frequency" value={product.interestRateFrequencyType.replace(/_/g, ' ')} />
              <InfoRow label="Interest Type" value={product.interestType.replace(/_/g, ' ')} />
              <InfoRow label="Amortization" value={product.amortizationType.replace(/_/g, ' ')} />
              <InfoRow label="Repay Every" value={`${product.repaymentEvery} ${product.repaymentFrequencyType.toLowerCase()}`} />
              <InfoRow label="Number of Repayments" value={String(product.numberOfRepayments)} />
              <InfoRow label="Grace on Principal" value={product.graceOnPrincipalPayment != null ? String(product.graceOnPrincipalPayment) : '—'} />
              <InfoRow label="Grace on Interest" value={product.graceOnInterestPayment != null ? String(product.graceOnInterestPayment) : '—'} />
              <InfoRow label="Days in Year" value={product.daysInYearType ?? '—'} />
            </dl>
          )}
        </div>
      )}

      {tab === 'gl' && (
        <div className="rounded-xl p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <dl className="grid grid-cols-2 gap-x-8 gap-y-4">
            <GlRow label="Fund Source" account={product.fundSourceAccount} />
            <GlRow label="Loan Portfolio" account={product.loanPortfolioAccount} />
            <GlRow label="Interest on Loan" account={product.interestOnLoanAccount} />
            <GlRow label="Income from Fees" account={product.incomeFromFeeAccount} />
            <GlRow label="Income from Penalties" account={product.incomeFromPenaltyAccount} />
            <GlRow label="Interest Receivable" account={product.interestReceivableAccount} />
            <GlRow label="Fee Receivable" account={product.feeReceivableAccount} />
            <GlRow label="Penalty Receivable" account={product.penaltyReceivableAccount} />
          </dl>
          <p className="mt-6 text-xs" style={{ color: 'var(--color-muted)' }}>GL account linkages are managed via the backend API. UI picker coming in a future release.</p>
        </div>
      )}

      {tab === 'charges' && (
        <div className="rounded-xl p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          {(product.charges?.length ?? 0) === 0 ? (
            <p className="text-sm text-center" style={{ color: 'var(--color-muted)' }}>No charges applied to this product.</p>
          ) : (
            <table className="w-full text-sm" style={{ borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--color-border)' }}>
                  {['Name', 'Calculation', 'Amount'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {product.charges!.map((c: import('../api/types').ChargeRef) => (
                  <tr key={c.id} style={{ borderBottom: '1px solid var(--color-border)', height: 44 }}>
                    <td className="px-4 py-2 font-medium" style={{ color: 'var(--color-text)' }}>{c.name}</td>
                    <td className="px-4 py-2" style={{ color: 'var(--color-muted)' }}>{c.chargeCalculationType.replace(/_/g, ' ')}</td>
                    <td className="px-4 py-2 tabular-nums" style={{ color: 'var(--color-text)' }}>{c.amount.toLocaleString()} {c.currencyCode}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* Delete confirm modal */}
      <Modal open={showDelete} onClose={() => setShowDelete(false)} title="Delete Loan Product" size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowDelete(false)} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={handleDelete} disabled={deleteMut.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-error)' }}>
              {deleteMut.isPending ? 'Deleting…' : 'Delete Product'}
            </button>
          </div>
        }>
        <p className="p-6 text-sm" style={{ color: 'var(--color-text)' }}>
          Delete <strong>{product.name}</strong>? This action cannot be undone.
        </p>
      </Modal>
    </div>
  )
}

// ── Shared form field groups ──────────────────────────────────────────────────

function LoanProductCoreFields({ form, setForm }: { form: CreateLoanProductRequest; setForm: React.Dispatch<React.SetStateAction<CreateLoanProductRequest>> }) {
  return (
    <>
      <div className="grid grid-cols-2 gap-4">
        <Field label="Name" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} required />
        <Field label="Short Name" value={form.shortName} maxLength={4} onChange={e => setForm(f => ({ ...f, shortName: e.target.value.toUpperCase() }))} required />
      </div>
      <Field label="Description" value={form.description ?? ''} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
      <Field label="Currency Code" value={form.currencyCode} onChange={e => setForm(f => ({ ...f, currencyCode: e.target.value.toUpperCase() }))} required />
      <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>Principal Range</p>
      <div className="grid grid-cols-3 gap-4">
        <Field label="Min" type="number" min="0" value={String(form.minPrincipalAmount)} onChange={e => setForm(f => ({ ...f, minPrincipalAmount: parseFloat(e.target.value) || 0 }))} />
        <Field label="Default" type="number" min="0" value={String(form.defaultPrincipalAmount)} onChange={e => setForm(f => ({ ...f, defaultPrincipalAmount: parseFloat(e.target.value) || 0 }))} />
        <Field label="Max" type="number" min="0" value={String(form.maxPrincipalAmount)} onChange={e => setForm(f => ({ ...f, maxPrincipalAmount: parseFloat(e.target.value) || 0 }))} />
      </div>
    </>
  )
}

function LoanProductInterestFields({ form, setForm }: { form: CreateLoanProductRequest; setForm: React.Dispatch<React.SetStateAction<CreateLoanProductRequest>> }) {
  return (
    <>
      <div className="grid grid-cols-3 gap-4">
        <Field label="Min Rate (%)" type="number" step="0.01" value={String(form.minInterestRatePerPeriod ?? '')} onChange={e => setForm(f => ({ ...f, minInterestRatePerPeriod: parseFloat(e.target.value) || undefined }))} />
        <Field label="Default Rate (%)" type="number" step="0.01" value={String(form.defaultInterestRatePerPeriod)} onChange={e => setForm(f => ({ ...f, defaultInterestRatePerPeriod: parseFloat(e.target.value) || 0 }))} required />
        <Field label="Max Rate (%)" type="number" step="0.01" value={String(form.maxInterestRatePerPeriod ?? '')} onChange={e => setForm(f => ({ ...f, maxInterestRatePerPeriod: parseFloat(e.target.value) || undefined }))} />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <Select label="Interest Type" value={form.interestType} onChange={v => setForm(f => ({ ...f, interestType: v as InterestType }))}
          options={[{ label: 'Declining Balance', value: 'DECLINING_BALANCE' }, { label: 'Flat', value: 'FLAT' }]} />
        <Select label="Amortization" value={form.amortizationType} onChange={v => setForm(f => ({ ...f, amortizationType: v as AmortizationType }))}
          options={[{ label: 'Equal Installments', value: 'EQUAL_INSTALLMENTS' }, { label: 'Equal Principal', value: 'EQUAL_PRINCIPAL' }]} />
      </div>
      <div className="grid grid-cols-3 gap-4">
        <Field label="Repay Every" type="number" min="1" value={String(form.repaymentEvery)} onChange={e => setForm(f => ({ ...f, repaymentEvery: parseInt(e.target.value) || 1 }))} />
        <Select label="Frequency" value={form.repaymentFrequencyType} onChange={v => setForm(f => ({ ...f, repaymentFrequencyType: v as RepaymentFrequencyType }))}
          options={[{ label: 'Months', value: 'MONTHS' }, { label: 'Weeks', value: 'WEEKS' }, { label: 'Days', value: 'DAYS' }]} />
        <Field label="# Repayments" type="number" min="1" value={String(form.numberOfRepayments)} onChange={e => setForm(f => ({ ...f, numberOfRepayments: parseInt(e.target.value) || 1 }))} />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <Field label="Grace on Principal" type="number" min="0" value={String(form.graceOnPrincipalPayment ?? '')} onChange={e => setForm(f => ({ ...f, graceOnPrincipalPayment: parseInt(e.target.value) || undefined }))} />
        <Field label="Grace on Interest" type="number" min="0" value={String(form.graceOnInterestPayment ?? '')} onChange={e => setForm(f => ({ ...f, graceOnInterestPayment: parseInt(e.target.value) || undefined }))} />
      </div>
      <Select label="Rate Frequency" value={form.interestRateFrequencyType} onChange={v => setForm(f => ({ ...f, interestRateFrequencyType: v as InterestRateFrequencyType }))}
        options={[{ label: 'Per Year', value: 'PER_YEAR' }, { label: 'Per Month', value: 'PER_MONTH' }, { label: 'Per Week', value: 'PER_WEEK' }]} />
    </>
  )
}

function LoanProductFormFields({ form, setForm }: { form: CreateLoanProductRequest; setForm: React.Dispatch<React.SetStateAction<CreateLoanProductRequest>> }) {
  return (
    <>
      <LoanProductCoreFields form={form} setForm={setForm} />
      <LoanProductInterestFields form={form} setForm={setForm} />
    </>
  )
}

// ── Primitive helpers ─────────────────────────────────────────────────────────

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium mb-0.5" style={{ color: 'var(--color-muted)' }}>{label}</dt>
      <dd className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>{value}</dd>
    </div>
  )
}

function GlRow({ label, account }: { label: string; account?: { glCode: string; name: string } }) {
  return (
    <div>
      <dt className="text-xs font-medium mb-0.5" style={{ color: 'var(--color-muted)' }}>{label}</dt>
      <dd className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>
        {account ? <span><span className="font-mono text-xs mr-2">{account.glCode}</span>{account.name}</span> : '—'}
      </dd>
    </div>
  )
}

function ActionBtn({ children, onClick, danger }: { children: React.ReactNode; onClick: () => void; danger?: boolean }) {
  return (
    <button onClick={onClick} className="px-3 py-1.5 text-xs font-medium rounded-lg"
      style={{ border: `1px solid ${danger ? 'var(--color-error)' : 'var(--color-border)'}`, color: danger ? 'var(--color-error)' : 'var(--color-text)' }}>
      {children}
    </button>
  )
}

function EditActions({ onSave, onCancel, isPending }: { onSave: () => void; onCancel: () => void; isPending: boolean }) {
  return (
    <div className="flex justify-end gap-3 pt-2" style={{ borderTop: '1px solid var(--color-border)' }}>
      <button onClick={onCancel} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
      <button onClick={onSave} disabled={isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
        {isPending ? 'Saving…' : 'Save Changes'}
      </button>
    </div>
  )
}

function ErrBox({ msg }: { msg: string }) {
  return <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{msg}</p>
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
