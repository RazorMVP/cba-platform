// web-react/src/app/features/cards/DisputesPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useCardDisputes, useRaiseDispute, useDisputeAction, useResolveDispute, useChargebackReasonCodes } from './api/useCards'
import type { CardDispute, DisputeStatus, DisputeReason, DisputeRequest, ResolveDisputeRequest } from './api/types'

const STATUS_OPTS: Array<DisputeStatus | ''> = ['', 'RAISED', 'RETRIEVAL_REQUESTED', 'CHARGEBACK_INITIATED', 'REPRESENTMENT', 'PRE_ARBITRATION', 'RESOLVED', 'WITHDRAWN']
const REASONS: DisputeReason[] = ['UNAUTHORIZED', 'GOODS_NOT_RECEIVED', 'DUPLICATE', 'AMOUNT_MISMATCH', 'OTHER']

function disputeVariant(s: DisputeStatus): 'warning' | 'neutral' | 'success' | 'error' | 'info' {
  if (s === 'RESOLVED')  return 'success'
  if (s === 'WITHDRAWN') return 'neutral'
  if (s === 'CHARGEBACK_INITIATED' || s === 'PRE_ARBITRATION') return 'error'
  return 'warning'
}

function ActionBtn({ label, color, onClick, disabled }: { label: string; color: string; onClick: () => void; disabled?: boolean }) {
  return (
    <button onClick={onClick} disabled={disabled}
      className="text-xs px-3 py-1.5 rounded-lg disabled:opacity-50"
      style={{ border: `1px solid ${color}`, color }}>
      {label}
    </button>
  )
}

export default function DisputesPage() {
  const [statusFilter, setStatusFilter] = useState<DisputeStatus | ''>('')
  const [selected, setSelected]         = useState<CardDispute | null>(null)
  const [showRaise, setShowRaise]       = useState(false)
  const [showResolve, setShowResolve]   = useState(false)
  const [raiseForm, setRaiseForm]       = useState<DisputeRequest>({ cardId: '', transactionRef: '', disputeReason: 'UNAUTHORIZED', originalAmount: 0 })
  const [resolveForm, setResolveForm]   = useState<ResolveDisputeRequest>({ resolvedBy: '', resolutionFavor: 'ISSUER' })
  const [error, setError]               = useState('')

  const { data, isLoading } = useCardDisputes(statusFilter || undefined)
  const disputes = (data as { data?: CardDispute[] } | undefined)?.data ?? []

  useChargebackReasonCodes() // prefetch reason codes for future chargeback UI

  const raise   = useRaiseDispute()
  const resolve = useResolveDispute(selected?.id ?? '')
  const action  = useDisputeAction(selected?.id ?? '')

  async function doRaise() {
    if (!raiseForm.cardId || !raiseForm.transactionRef) { setError('Card ID and transaction ref required'); return }
    setError('')
    try { await raise.mutateAsync(raiseForm); setShowRaise(false) } catch (e) { setError(e instanceof Error ? e.message : 'Failed') }
  }

  async function doResolve() {
    if (!resolveForm.resolvedBy) { setError('Resolved by is required'); return }
    setError('')
    try { await resolve.mutateAsync(resolveForm); setShowResolve(false) } catch (e) { setError(e instanceof Error ? e.message : 'Failed') }
  }

  async function doAction(act: string) {
    try { await action.mutateAsync({ action: act }) } catch { /* silent */ }
  }

  const s = selected

  return (
    <div>
      <PageHeader
        title="Disputes"
        subtitle="Chargeback workflow management across all card schemes"
        actions={
          <button onClick={() => { setRaiseForm({ cardId: '', transactionRef: '', disputeReason: 'UNAUTHORIZED', originalAmount: 0 }); setError(''); setShowRaise(true) }}
            className="px-4 py-2 rounded-lg text-sm font-medium text-white"
            style={{ background: 'var(--color-primary)' }}>+ Raise Dispute</button>
        }
      />

      {/* Status filter */}
      <div className="flex flex-wrap gap-2 mb-4">
        {STATUS_OPTS.map(opt => (
          <button key={opt} onClick={() => setStatusFilter(opt)}
            className="px-3 py-1.5 rounded-full text-xs font-medium transition-colors"
            style={{
              background: statusFilter === opt ? 'var(--color-primary)' : 'var(--bg-subtle)',
              color:      statusFilter === opt ? '#fff' : 'var(--color-muted)',
              border:     '1px solid var(--color-border)',
            }}>
            {opt || 'All'}
          </button>
        ))}
      </div>

      <div className="flex gap-4">
        {/* List */}
        <div className="flex-1 rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
          {isLoading ? (
            <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
          ) : (
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                  {['Card', 'Txn Ref', 'Reason', 'Amount', 'Status', 'Raised'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                      style={{ color: 'var(--color-muted)' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {disputes.length === 0 && (
                  <tr><td colSpan={6} className="px-4 py-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>No disputes found.</td></tr>
                )}
                {disputes.map(d => (
                  <tr key={d.id} onClick={() => setSelected(d)}
                    className="cursor-pointer"
                    style={{
                      borderBottom: '1px solid var(--color-border)',
                      background: selected?.id === d.id ? 'var(--bg-subtle)' : 'var(--bg-card)',
                    }}
                    onMouseEnter={e => { if (selected?.id !== d.id) e.currentTarget.style.background = 'var(--bg-subtle)' }}
                    onMouseLeave={e => { if (selected?.id !== d.id) e.currentTarget.style.background = 'var(--bg-card)' }}>
                    <td className="px-4 py-2 font-mono text-xs" style={{ color: 'var(--color-text)' }}>{d.cardId.slice(0, 8)}</td>
                    <td className="px-4 py-2 font-mono text-xs" style={{ color: 'var(--color-muted)' }}>{d.transactionRef}</td>
                    <td className="px-4 py-2 text-xs" style={{ color: 'var(--color-text)' }}>{d.disputeReason.replace(/_/g, ' ')}</td>
                    <td className="px-4 py-2 tabular-nums text-sm" style={{ color: 'var(--color-text)' }}>
                      {d.originalAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                    </td>
                    <td className="px-4 py-2"><StatusBadge label={d.status} variant={disputeVariant(d.status)} /></td>
                    <td className="px-4 py-2 text-xs" style={{ color: 'var(--color-muted)' }}>{new Date(d.createdAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Detail panel */}
        {s && (
          <div className="w-80 shrink-0 rounded-xl p-5" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
            <div className="flex items-start justify-between mb-4">
              <p className="text-sm font-semibold" style={{ color: 'var(--color-text)' }}>Dispute Detail</p>
              <button onClick={() => setSelected(null)} className="text-xs" style={{ color: 'var(--color-muted)' }}>✕</button>
            </div>
            <div className="space-y-2 text-xs mb-5">
              {[
                ['Status',   <StatusBadge label={s.status} variant={disputeVariant(s.status)} />],
                ['Reason',   s.disputeReason.replace(/_/g, ' ')],
                ['Amount',   s.originalAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })],
                ['Raised by', s.raisedBy.slice(0, 8)],
                ['Txn Ref',  s.transactionRef],
                ['CB Deadline', s.chargebackDeadline ?? '—'],
                ['Resp Deadline', s.responseDeadline ?? '—'],
                ['Resolution', s.resolutionNotes ?? '—'],
                ['Favor',    s.resolutionFavor ?? '—'],
              ].map(([label, val]) => (
                <div key={String(label)} className="flex justify-between gap-2">
                  <span style={{ color: 'var(--color-muted)' }}>{label}</span>
                  <span className="font-medium text-right" style={{ color: 'var(--color-text)' }}>{val as React.ReactNode}</span>
                </div>
              ))}
            </div>

            {/* Workflow actions */}
            <p className="text-xs font-semibold mb-2" style={{ color: 'var(--color-muted)' }}>ACTIONS</p>
            <div className="flex flex-wrap gap-2">
              {s.status === 'RAISED' && (
                <ActionBtn label="Request Retrieval" color="var(--color-info)" onClick={() => doAction('retrieval')} />
              )}
              {(s.status === 'RAISED' || s.status === 'RETRIEVAL_REQUESTED') && (
                <ActionBtn label="Initiate Chargeback" color="var(--color-warning)" onClick={() => doAction('chargeback')} />
              )}
              {s.status === 'CHARGEBACK_INITIATED' && (
                <ActionBtn label="Record Representment" color="var(--color-warning)" onClick={() => doAction('representment')} />
              )}
              {s.status === 'REPRESENTMENT' && (
                <ActionBtn label="Escalate Pre-Arbitration" color="var(--color-error)" onClick={() => doAction('pre-arbitration')} />
              )}
              {!['RESOLVED', 'WITHDRAWN'].includes(s.status) && (
                <>
                  <ActionBtn label="Resolve" color="var(--color-success)" onClick={() => { setError(''); setShowResolve(true) }} />
                  <ActionBtn label="Withdraw" color="var(--color-muted)" onClick={() => doAction('withdraw')} />
                </>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Raise Dispute Modal */}
      <Modal open={showRaise} onClose={() => setShowRaise(false)} title="Raise Dispute" size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowRaise(false)} className="px-4 py-2 text-sm rounded-lg"
              style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={doRaise} disabled={raise.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}>
              {raise.isPending ? 'Raising…' : 'Raise'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-3">
          {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
          <RF label="Card ID" value={raiseForm.cardId} onChange={e => setRaiseForm(p => ({ ...p, cardId: e.target.value }))} />
          <RF label="Transaction Reference (RRN)" value={raiseForm.transactionRef} onChange={e => setRaiseForm(p => ({ ...p, transactionRef: e.target.value }))} />
          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Dispute Reason</label>
            <select value={raiseForm.disputeReason} onChange={e => setRaiseForm(p => ({ ...p, disputeReason: e.target.value as DisputeReason }))}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
              {REASONS.map(r => <option key={r} value={r}>{r.replace(/_/g, ' ')}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Original Amount</label>
            <input type="number" min={0} step="0.01" value={raiseForm.originalAmount}
              onChange={e => setRaiseForm(p => ({ ...p, originalAmount: parseFloat(e.target.value) || 0 }))}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
          </div>
        </div>
      </Modal>

      {/* Resolve Modal */}
      <Modal open={showResolve} onClose={() => setShowResolve(false)} title="Resolve Dispute" size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowResolve(false)} className="px-4 py-2 text-sm rounded-lg"
              style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={doResolve} disabled={resolve.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}>
              {resolve.isPending ? 'Resolving…' : 'Resolve'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-3">
          {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
          <RF label="Resolved By (staff ID)" value={resolveForm.resolvedBy} onChange={e => setResolveForm(p => ({ ...p, resolvedBy: e.target.value }))} />
          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Resolution Favor</label>
            <select value={resolveForm.resolutionFavor} onChange={e => setResolveForm(p => ({ ...p, resolutionFavor: e.target.value }))}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
              <option value="ISSUER">ISSUER</option>
              <option value="ACQUIRER">ACQUIRER</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Resolution Notes (optional)</label>
            <textarea rows={3} value={resolveForm.resolutionNotes ?? ''}
              onChange={e => setResolveForm(p => ({ ...p, resolutionNotes: e.target.value || undefined }))}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none resize-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
          </div>
        </div>
      </Modal>
    </div>
  )
}

function RF({ label, ...props }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <input {...props} className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
    </div>
  )
}
