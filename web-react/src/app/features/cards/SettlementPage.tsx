// web-react/src/app/features/cards/SettlementPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { useSettlementBatches, useCloseBatch, useExportBatch, useSettlementTransmissions } from './api/useCards'
import type { SettlementBatch } from './api/types'

function batchVariant(s: string): 'warning' | 'neutral' | 'success' | 'error' {
  if (s === 'OPEN')     return 'warning'
  if (s === 'SETTLED')  return 'success'
  if (s === 'FAILED')   return 'error'
  return 'neutral'
}
function txVariant(s: string): 'warning' | 'neutral' | 'success' | 'error' {
  if (s === 'PENDING')      return 'warning'
  if (s === 'TRANSMITTED')  return 'success'
  if (s === 'ACKNOWLEDGED') return 'success'
  if (s === 'FAILED')       return 'error'
  return 'neutral'
}

function BatchRow({ batch }: { batch: SettlementBatch }) {
  const [expanded, setExpanded] = useState(false)
  const close  = useCloseBatch(batch.id)
  const export_ = useExportBatch(batch.id)

  const { data: txData } = useSettlementTransmissions(expanded ? batch.id : undefined)
  const transmissions = (txData as { data?: import('./api/types').SettlementTransmission[] } | undefined)?.data ?? []

  return (
    <>
      <tr style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
        <td className="px-4 py-3">
          <button onClick={() => setExpanded(e => !e)} className="text-xs mr-2" style={{ color: 'var(--color-muted)' }}>
            {expanded ? '▼' : '▶'}
          </button>
          <span className="font-mono text-xs" style={{ color: 'var(--color-text)' }}>{batch.batchRef}</span>
        </td>
        <td className="px-4 py-3"><StatusBadge label={batch.status} variant={batchVariant(batch.status)} /></td>
        <td className="px-4 py-3 text-sm" style={{ color: 'var(--color-muted)' }}>{batch.settlementDate}</td>
        <td className="px-4 py-3 tabular-nums text-sm" style={{ color: 'var(--color-text)' }}>
          {batch.totalAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
        </td>
        <td className="px-4 py-3 tabular-nums text-sm" style={{ color: 'var(--color-muted)' }}>{batch.itemCount}</td>
        <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>
          {new Date(batch.openedAt).toLocaleString()}
        </td>
        <td className="px-4 py-3">
          <div className="flex gap-2">
            {batch.status === 'OPEN' && (
              <button onClick={() => close.mutate()} disabled={close.isPending}
                className="text-xs px-2 py-1 rounded disabled:opacity-50"
                style={{ border: '1px solid var(--color-warning)', color: 'var(--color-warning)' }}>
                {close.isPending ? '…' : 'Close'}
              </button>
            )}
            {batch.status === 'CLOSED' && (
              <button onClick={() => export_.mutate()} disabled={export_.isPending}
                className="text-xs px-2 py-1 rounded disabled:opacity-50"
                style={{ border: '1px solid var(--color-primary)', color: 'var(--color-primary)' }}>
                {export_.isPending ? '…' : 'Export'}
              </button>
            )}
          </div>
        </td>
      </tr>
      {expanded && (
        <tr style={{ background: 'var(--bg-subtle)' }}>
          <td colSpan={7} className="px-6 py-4">
            <p className="text-xs font-semibold mb-2" style={{ color: 'var(--color-muted)' }}>
              SCHEME TRANSMISSIONS
            </p>
            {transmissions.length === 0 ? (
              <p className="text-xs" style={{ color: 'var(--color-muted)' }}>No transmissions yet.</p>
            ) : (
              <table className="w-full text-xs border-collapse">
                <thead>
                  <tr>
                    {['Scheme', 'Status', 'Attempts', 'Last Attempt', 'Endpoint'].map(h => (
                      <th key={h} className="text-left pb-1 pr-6 font-semibold uppercase tracking-wider"
                        style={{ color: 'var(--color-muted)' }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {transmissions.map(t => (
                    <tr key={t.id}>
                      <td className="py-1 pr-6 font-mono" style={{ color: 'var(--color-text)' }}>{t.scheme}</td>
                      <td className="py-1 pr-6"><StatusBadge label={t.status} variant={txVariant(t.status)} /></td>
                      <td className="py-1 pr-6 tabular-nums" style={{ color: 'var(--color-muted)' }}>{t.attemptCount}</td>
                      <td className="py-1 pr-6" style={{ color: 'var(--color-muted)' }}>
                        {t.lastAttemptAt ? new Date(t.lastAttemptAt).toLocaleString() : '—'}
                      </td>
                      <td className="py-1" style={{ color: 'var(--color-muted)' }}>{t.endpoint ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </td>
        </tr>
      )}
    </>
  )
}

export default function SettlementPage() {
  const { data, isLoading } = useSettlementBatches()
  const batches = (data as { data?: SettlementBatch[] } | undefined)?.data ?? []

  return (
    <div>
      <PageHeader
        title="Settlement"
        subtitle="Manage settlement batches and scheme transmission status"
      />

      {isLoading ? (
        <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
      ) : (
        <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                {['Batch Ref', 'Status', 'Settlement Date', 'Total Amount', 'Items', 'Opened', ''].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                    style={{ color: 'var(--color-muted)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {batches.length === 0 && (
                <tr><td colSpan={7} className="px-4 py-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>No settlement batches.</td></tr>
              )}
              {batches.map(b => <BatchRow key={b.id} batch={b} />)}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
