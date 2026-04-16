// web-react/src/app/features/operations/payments/PaymentsListPage.tsx
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { DataTable, type ColumnDef } from '@/shared/components/DataTable'
import { StatusBadge, type BadgeVariant } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { usePayments, useTransfer } from '../api/usePayments'
import type { Payment, PaymentStatus } from '../api/types'

function statusVariant(s: PaymentStatus): BadgeVariant {
  const m: Record<PaymentStatus, BadgeVariant> = {
    COMPLETED: 'success', PENDING: 'warning', PROCESSING: 'info',
    FAILED: 'error', REVERSED: 'neutral',
  }
  return m[s]
}

const PAGE_SIZE = 20

const columns: ColumnDef<Payment>[] = [
  {
    key: 'ref', header: 'Reference',
    cell: r => <Link to={`/payments/${r.id}`} className="font-medium hover:underline tabular-nums" style={{ color: 'var(--color-info)' }}>{r.referenceNumber}</Link>,
  },
  { key: 'from', header: 'From', cell: r => r.sourceAccountNumber },
  { key: 'to', header: 'To', cell: r => r.destinationAccountNumber ?? '—' },
  {
    key: 'amount', header: 'Amount', numeric: true,
    cell: r => (
      <span className="tabular-nums">
        {r.amount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
        {' '}
        <span style={{ color: 'var(--color-muted)' }}>{r.currencyCode}</span>
        {r.isCrossCurrency && <span className="ml-1 text-xs" style={{ color: 'var(--color-accent)' }}>FX</span>}
      </span>
    ),
  },
  { key: 'type', header: 'Type', cell: r => r.paymentType.replace(/_/g, ' ') },
  { key: 'status', header: 'Status', cell: r => <StatusBadge label={r.status} variant={statusVariant(r.status)} /> },
  { key: 'date', header: 'Date', numeric: true, cell: r => new Date(r.createdAt).toLocaleString() },
]

export default function PaymentsListPage() {
  const [page, setPage] = useState(0)
  const [showTransfer, setShowTransfer] = useState(false)

  const { data, isLoading } = usePayments({ page, size: PAGE_SIZE })
  const payments = data?.data ?? []
  const total = data?.meta?.total ?? 0
  const totalPages = Math.ceil(total / PAGE_SIZE)

  return (
    <div>
      <PageHeader
        title="Payments"
        subtitle={`${total.toLocaleString()} total`}
        actions={
          <button onClick={() => setShowTransfer(true)}
            className="px-4 py-2 rounded-lg text-sm font-medium text-white"
            style={{ background: 'var(--color-primary)' }}>
            + New Transfer
          </button>
        }
      />

      <div className="rounded-xl" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <DataTable columns={columns} data={payments} loading={isLoading}
          emptyMessage="No payments found" getRowKey={r => r.id} />
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

      <TransferModal open={showTransfer} onClose={() => setShowTransfer(false)} />
    </div>
  )
}

// ── 3-step Transfer Wizard ───────────────────────────────────────

type WizardStep = 1 | 2 | 3

interface TransferForm {
  sourceAccountId: string
  destinationAccountId: string
  amount: string
  description: string
}

function TransferModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const transfer = useTransfer()
  const [step, setStep] = useState<WizardStep>(1)
  const [form, setForm] = useState<TransferForm>({ sourceAccountId: '', destinationAccountId: '', amount: '', description: '' })
  const [error, setError] = useState('')

  function reset() { setStep(1); setForm({ sourceAccountId: '', destinationAccountId: '', amount: '', description: '' }); setError('') }

  async function handleConfirm() {
    setError('')
    try {
      await transfer.mutateAsync({
        sourceAccountId: form.sourceAccountId,
        destinationAccountId: form.destinationAccountId,
        amount: parseFloat(form.amount),
        description: form.description,
      })
      reset(); onClose()
    } catch (err) { setError(err instanceof Error ? err.message : 'Transfer failed') }
  }

  const stepLabels = ['Source', 'Destination & Amount', 'Confirm']

  return (
    <Modal open={open} onClose={() => { reset(); onClose() }} title="New Transfer" size="md"
      footer={
        <div className="flex justify-between">
          <button onClick={() => step > 1 ? setStep(s => (s - 1) as WizardStep) : (reset(), onClose())}
            className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
            {step === 1 ? 'Cancel' : '← Back'}
          </button>
          <button
            onClick={() => step < 3 ? setStep(s => (s + 1) as WizardStep) : handleConfirm()}
            disabled={
              (step === 1 && !form.sourceAccountId) ||
              (step === 2 && (!form.destinationAccountId || !form.amount)) ||
              transfer.isPending
            }
            className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {step < 3 ? 'Next →' : transfer.isPending ? 'Processing…' : 'Confirm Transfer'}
          </button>
        </div>
      }>
      <div className="p-6">
        {/* Step indicator */}
        <div className="flex items-center gap-2 mb-6">
          {stepLabels.map((label, i) => {
            const n = (i + 1) as WizardStep
            return (
              <div key={label} className="flex items-center gap-2">
                <div className="flex items-center gap-1.5">
                  <div className="w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold"
                    style={{ background: step >= n ? 'var(--color-primary)' : 'var(--bg-subtle)', color: step >= n ? '#fff' : 'var(--color-muted)' }}>
                    {n}
                  </div>
                  <span className="text-xs font-medium" style={{ color: step === n ? 'var(--color-text)' : 'var(--color-muted)' }}>{label}</span>
                </div>
                {i < stepLabels.length - 1 && <div className="h-px flex-1 min-w-[16px]" style={{ background: 'var(--color-border)' }} />}
              </div>
            )
          })}
        </div>

        {error && <p className="mb-4 text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}

        {step === 1 && (
          <Field label="Source Account ID" value={form.sourceAccountId}
            onChange={e => setForm(f => ({ ...f, sourceAccountId: e.target.value }))} required
            placeholder="Enter source account UUID" />
        )}

        {step === 2 && (
          <div className="space-y-4">
            <Field label="Destination Account ID" value={form.destinationAccountId}
              onChange={e => setForm(f => ({ ...f, destinationAccountId: e.target.value }))} required
              placeholder="Enter destination account UUID" />
            <Field label="Amount" type="number" min="0.01" step="0.01" value={form.amount}
              onChange={e => setForm(f => ({ ...f, amount: e.target.value }))} required />
            <Field label="Description (optional)" value={form.description}
              onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
          </div>
        )}

        {step === 3 && (
          <div className="space-y-3">
            <p className="text-xs font-semibold uppercase tracking-wider mb-4" style={{ color: 'var(--color-muted)' }}>Review Transfer Details</p>
            {[
              { label: 'From', value: form.sourceAccountId },
              { label: 'To', value: form.destinationAccountId },
              { label: 'Amount', value: `${parseFloat(form.amount).toLocaleString(undefined, { minimumFractionDigits: 2 })}` },
              { label: 'Description', value: form.description || '—' },
            ].map(row => (
              <div key={row.label} className="flex justify-between py-2" style={{ borderBottom: '1px solid var(--color-border)' }}>
                <span className="text-xs" style={{ color: 'var(--color-muted)' }}>{row.label}</span>
                <span className="text-sm font-medium tabular-nums" style={{ color: 'var(--color-text)' }}>{row.value}</span>
              </div>
            ))}
          </div>
        )}
      </div>
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
