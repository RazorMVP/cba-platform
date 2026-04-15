// web-react/src/app/features/operations/payments/PaymentDetailPage.tsx
import { useParams, Link } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge, type BadgeVariant } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useState } from 'react'
import { usePayment, useReversePayment } from '../api/usePayments'
import type { PaymentStatus } from '../api/types'

function statusVariant(s: PaymentStatus): BadgeVariant {
  const m: Record<PaymentStatus, BadgeVariant> = {
    COMPLETED: 'success', PENDING: 'warning', PROCESSING: 'info',
    FAILED: 'error', REVERSED: 'neutral',
  }
  return m[s]
}

function statusColor(s: PaymentStatus): string {
  const m: Record<PaymentStatus, string> = {
    COMPLETED: 'var(--color-success-bg)', PENDING: 'var(--color-warning-bg)',
    PROCESSING: 'var(--color-info-bg)', FAILED: 'var(--color-error-bg)', REVERSED: 'var(--bg-subtle)',
  }
  return m[s]
}

export default function PaymentDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [showReverse, setShowReverse] = useState(false)
  const { data: payment, isLoading } = usePayment(id ?? '')
  const reverse = useReversePayment(id ?? '')

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
  if (!payment) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Payment not found.</div>

  return (
    <div>
      <PageHeader
        title={payment.referenceNumber}
        subtitle={payment.paymentType.replace(/_/g, ' ')}
        actions={
          <div className="flex items-center gap-3">
            <StatusBadge label={payment.status} variant={statusVariant(payment.status)} />
            <Link to="/payments" className="text-sm" style={{ color: 'var(--color-muted)' }}>← Back</Link>
          </div>
        }
      />

      {/* Status band */}
      <div className="rounded-xl p-5 mb-6" style={{ background: statusColor(payment.status), border: '1px solid var(--color-border)' }}>
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider mb-1" style={{ color: 'var(--color-muted)' }}>Amount</p>
            <p className="font-display text-3xl font-bold tabular-nums" style={{ color: 'var(--color-text)' }}>
              {payment.amount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
              <span className="text-lg ml-2" style={{ color: 'var(--color-muted)' }}>{payment.currencyCode}</span>
            </p>
            {payment.isCrossCurrency && (
              <p className="mt-1 text-xs" style={{ color: 'var(--color-muted)' }}>
                FX: {payment.sourceAmount?.toLocaleString()} {payment.sourceCurrency} →{' '}
                {payment.destinationAmount?.toLocaleString()} {payment.destinationCurrency}{' '}
                @ {payment.exchangeRateUsed?.toFixed(4)}
              </p>
            )}
          </div>
          {payment.status === 'COMPLETED' && (
            <button onClick={() => setShowReverse(true)} className="px-3 py-1.5 text-xs font-medium rounded-lg"
              style={{ border: '1px solid var(--color-error)', color: 'var(--color-error)' }}>
              Reverse Payment
            </button>
          )}
        </div>
      </div>

      {/* Transfer route card */}
      <div className="rounded-xl p-6 mb-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <p className="text-xs font-semibold uppercase tracking-wider mb-4" style={{ color: 'var(--color-muted)' }}>Transfer Route</p>
        <div className="flex items-center gap-4">
          <div className="flex-1 p-4 rounded-lg" style={{ background: 'var(--bg-subtle)' }}>
            <p className="text-xs mb-1" style={{ color: 'var(--color-muted)' }}>Source</p>
            <p className="font-medium text-sm tabular-nums" style={{ color: 'var(--color-text)' }}>
              <Link to={`/accounts/${payment.sourceAccountId}`} className="hover:underline" style={{ color: 'var(--color-info)' }}>
                {payment.sourceAccountNumber}
              </Link>
            </p>
          </div>
          <div className="text-2xl" style={{ color: 'var(--color-muted)' }}>→</div>
          <div className="flex-1 p-4 rounded-lg" style={{ background: 'var(--bg-subtle)' }}>
            <p className="text-xs mb-1" style={{ color: 'var(--color-muted)' }}>Destination</p>
            <p className="font-medium text-sm tabular-nums" style={{ color: 'var(--color-text)' }}>
              {payment.destinationAccountId ? (
                <Link to={`/accounts/${payment.destinationAccountId}`} className="hover:underline" style={{ color: 'var(--color-info)' }}>
                  {payment.destinationAccountNumber}
                </Link>
              ) : '—'}
            </p>
          </div>
        </div>
      </div>

      {/* Payment details */}
      <div className="rounded-xl p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <p className="text-xs font-semibold uppercase tracking-wider mb-4" style={{ color: 'var(--color-muted)' }}>Payment Details</p>
        <dl className="grid grid-cols-2 gap-x-8 gap-y-4">
          <InfoRow label="Reference Number" value={payment.referenceNumber} />
          <InfoRow label="Payment Type" value={payment.paymentType.replace(/_/g, ' ')} />
          <InfoRow label="Status" value={payment.status} />
          <InfoRow label="Currency" value={payment.currencyCode} />
          <InfoRow label="Description" value={payment.description ?? '—'} />
          <InfoRow label="Cross Currency" value={payment.isCrossCurrency ? 'Yes' : 'No'} />
          <InfoRow label="Date" value={new Date(payment.createdAt).toLocaleString()} />
          {payment.isCrossCurrency && (
            <InfoRow label="Exchange Rate" value={payment.exchangeRateUsed?.toFixed(6) ?? '—'} />
          )}
        </dl>
      </div>

      <Modal open={showReverse} onClose={() => setShowReverse(false)} title="Reverse Payment" size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowReverse(false)} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={async () => { await reverse.mutateAsync(); setShowReverse(false) }}
              disabled={reverse.isPending}
              className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-error)' }}>
              {reverse.isPending ? 'Reversing…' : 'Confirm Reversal'}
            </button>
          </div>
        }>
        <p className="p-6 text-sm" style={{ color: 'var(--color-text)' }}>
          Reverse this payment of <strong>{payment.amount.toLocaleString()} {payment.currencyCode}</strong>?
          This will debit the destination account and credit the source account.
        </p>
      </Modal>
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium mb-0.5" style={{ color: 'var(--color-muted)' }}>{label}</dt>
      <dd className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>{value}</dd>
    </div>
  )
}
