// web-react/src/app/features/operations/tellers/TellerDetailPage.tsx
import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge, type BadgeVariant } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import {
  useTeller, useTellerCashiers, useTellerSessions,
  useTellerCommand, useSessionTransaction, useSettleSession,
} from '../api/useTellers'
import type { TellerSession, TellerStatus } from '../api/types'

type Tab = 'overview' | 'cashiers' | 'sessions'

function tellerVariant(s: TellerStatus): BadgeVariant {
  const m: Record<TellerStatus, BadgeVariant> = { ACTIVE: 'success', INACTIVE: 'warning', CLOSED: 'neutral' }
  return m[s]
}

function sessionVariant(s: TellerSession['status']): BadgeVariant {
  return s === 'OPEN' ? 'info' : 'neutral'
}

export default function TellerDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [tab, setTab] = useState<Tab>('overview')
  const [expandedSessions, setExpandedSessions] = useState<Set<string>>(new Set())
  const [modal, setModal] = useState<string | null>(null)
  const [selectedSession, setSelectedSession] = useState<TellerSession | null>(null)
  const [txType, setTxType] = useState<'CASH_IN' | 'CASH_OUT'>('CASH_IN')
  const [txAmount, setTxAmount] = useState('')
  const [settleAmount, setSettleAmount] = useState('')
  const [error, setError] = useState('')

  const { data: teller, isLoading } = useTeller(id ?? '')
  const { data: cashiersData } = useTellerCashiers(id ?? '')
  const { data: sessionsData } = useTellerSessions(id ?? '')
  const command = useTellerCommand(id ?? '')

  const cashiers = cashiersData?.data ?? []
  const sessions = sessionsData?.data ?? []

  function toggleSession(sessionId: string) {
    setExpandedSessions(prev => {
      const next = new Set(prev)
      next.has(sessionId) ? next.delete(sessionId) : next.add(sessionId)
      return next
    })
  }

  async function handleTellerCommand(cmd: string) {
    try { await command.mutateAsync({ command: cmd }) } catch (_) { /* surface via UI state */ }
  }

  const sessionTx = useSessionTransaction(id ?? '', selectedSession?.id ?? '')
  const settle = useSettleSession(id ?? '', selectedSession?.id ?? '')

  async function handleCashTx() {
    setError('')
    try {
      await sessionTx.mutateAsync({ transactionType: txType, amount: parseFloat(txAmount) })
      setModal(null); setTxAmount('')
    } catch (err) { setError(err instanceof Error ? err.message : 'Transaction failed') }
  }

  async function handleSettle() {
    setError('')
    try {
      await settle.mutateAsync({ actualCash: parseFloat(settleAmount) })
      setModal(null); setSettleAmount('')
    } catch (err) { setError(err instanceof Error ? err.message : 'Settlement failed') }
  }

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
  if (!teller) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Teller not found.</div>

  const tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: 'Overview' },
    { key: 'cashiers', label: `Cashiers (${cashiers.length})` },
    { key: 'sessions', label: `Sessions (${sessions.length})` },
  ]

  return (
    <div>
      <PageHeader
        title={teller.name}
        subtitle={teller.officeName}
        actions={
          <div className="flex items-center gap-3">
            <StatusBadge label={teller.status} variant={tellerVariant(teller.status)} />
            <Link to="/tellers" className="text-sm" style={{ color: 'var(--color-muted)' }}>← Back</Link>
          </div>
        }
      />

      {/* Lifecycle buttons */}
      <div className="flex flex-wrap gap-2 mb-6">
        {teller.status === 'INACTIVE' && (
          <ActionBtn onClick={() => handleTellerCommand('activate')}>Activate</ActionBtn>
        )}
        {teller.status === 'ACTIVE' && (
          <ActionBtn onClick={() => handleTellerCommand('deactivate')} danger>Deactivate</ActionBtn>
        )}
      </div>

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

      {/* Tab: Overview */}
      {tab === 'overview' && (
        <div className="rounded-xl p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <dl className="grid grid-cols-2 gap-x-8 gap-y-4">
            <InfoRow label="Name" value={teller.name} />
            <InfoRow label="Office" value={teller.officeName} />
            <InfoRow label="Status" value={teller.status} />
            <InfoRow label="Description" value={teller.description ?? '—'} />
            <InfoRow label="Created" value={new Date(teller.createdAt).toLocaleDateString()} />
          </dl>
        </div>
      )}

      {/* Tab: Cashiers */}
      {tab === 'cashiers' && (
        <div className="rounded-xl" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          {cashiers.length === 0 ? (
            <p className="p-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>No cashiers assigned to this teller desk.</p>
          ) : (
            <table className="w-full text-sm" style={{ borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--color-border)' }}>
                  {['Staff', 'Shift', 'Start', 'End'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {cashiers.map(c => (
                  <tr key={c.id} style={{ borderBottom: '1px solid var(--color-border)', height: 44 }}>
                    <td className="px-4 py-2 font-medium" style={{ color: 'var(--color-text)' }}>{c.staffName}</td>
                    <td className="px-4 py-2" style={{ color: 'var(--color-muted)' }}>{c.isFullDay ? 'Full Day' : 'Part Day'}</td>
                    <td className="px-4 py-2 tabular-nums" style={{ color: 'var(--color-text)' }}>{c.startTime ?? '—'}</td>
                    <td className="px-4 py-2 tabular-nums" style={{ color: 'var(--color-text)' }}>{c.endTime ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* Tab: Sessions */}
      {tab === 'sessions' && (
        <div className="space-y-3">
          {sessions.length === 0 && (
            <p className="p-8 text-sm text-center rounded-xl" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', color: 'var(--color-muted)' }}>
              No sessions yet.
            </p>
          )}
          {sessions.map(session => (
            <div key={session.id} className="rounded-xl overflow-hidden" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
              {/* Session header row — clickable to expand */}
              <button
                onClick={() => toggleSession(session.id)}
                className="w-full flex items-center justify-between px-6 py-4 text-left"
                style={{ cursor: 'pointer' }}
              >
                <div className="flex items-center gap-4">
                  <StatusBadge label={session.status} variant={sessionVariant(session.status)} />
                  <span className="text-sm font-medium tabular-nums" style={{ color: 'var(--color-text)' }}>
                    {new Date(session.sessionDate).toLocaleDateString()}
                  </span>
                  <span className="text-sm" style={{ color: 'var(--color-muted)' }}>{session.cashierName}</span>
                </div>
                <div className="flex items-center gap-6">
                  <div className="text-right">
                    <p className="text-xs" style={{ color: 'var(--color-muted)' }}>Opening</p>
                    <p className="text-sm tabular-nums font-medium" style={{ color: 'var(--color-text)' }}>
                      {session.openingBalance.toLocaleString()} {session.currencyCode}
                    </p>
                  </div>
                  {session.closingBalance !== undefined && (
                    <div className="text-right">
                      <p className="text-xs" style={{ color: 'var(--color-muted)' }}>Closing</p>
                      <p className="text-sm tabular-nums font-medium" style={{ color: 'var(--color-text)' }}>
                        {session.closingBalance.toLocaleString()} {session.currencyCode}
                      </p>
                    </div>
                  )}
                  <span className="text-xs" style={{ color: 'var(--color-muted)' }}>
                    {expandedSessions.has(session.id) ? '▲' : '▼'}
                  </span>
                </div>
              </button>

              {/* Expanded session actions */}
              {expandedSessions.has(session.id) && (
                <div className="px-6 pb-4" style={{ borderTop: '1px solid var(--color-border)' }}>
                  <div className="flex flex-wrap gap-2 pt-4">
                    {session.status === 'OPEN' && (
                      <>
                        <ActionBtn onClick={() => { setSelectedSession(session); setTxType('CASH_IN'); setModal('cashTx') }}>Cash In</ActionBtn>
                        <ActionBtn onClick={() => { setSelectedSession(session); setTxType('CASH_OUT'); setModal('cashTx') }}>Cash Out</ActionBtn>
                        <ActionBtn onClick={() => { setSelectedSession(session); setModal('settle') }}>Settle Session</ActionBtn>
                      </>
                    )}
                    {session.difference !== undefined && (
                      <span className="text-xs px-3 py-1.5 rounded-lg" style={{
                        background: session.difference === 0 ? 'var(--color-success-bg)' : 'var(--color-warning-bg)',
                        color: session.difference === 0 ? 'var(--color-success)' : 'var(--color-warning)',
                      }}>
                        Difference: {session.difference.toLocaleString()} {session.currencyCode}
                      </span>
                    )}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Cash transaction modal */}
      <Modal open={modal === 'cashTx'} onClose={() => { setModal(null); setError('') }}
        title={txType === 'CASH_IN' ? 'Cash In' : 'Cash Out'} size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => { setModal(null); setError('') }} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={handleCashTx} disabled={sessionTx.isPending || !txAmount}
              className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
              {sessionTx.isPending ? 'Processing…' : 'Confirm'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-4">
          {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
          <Field label="Amount" type="number" min="0.01" step="0.01" value={txAmount} onChange={e => setTxAmount(e.target.value)} required />
        </div>
      </Modal>

      {/* Settle session modal */}
      <Modal open={modal === 'settle'} onClose={() => { setModal(null); setError('') }} title="Settle Session" size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => { setModal(null); setError('') }} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={handleSettle} disabled={settle.isPending || !settleAmount}
              className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
              {settle.isPending ? 'Settling…' : 'Settle & Close'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-4">
          {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
          <p className="text-sm" style={{ color: 'var(--color-muted)' }}>Enter the actual cash counted at close of session.</p>
          <Field label="Actual Cash" type="number" min="0" step="0.01" value={settleAmount} onChange={e => setSettleAmount(e.target.value)} required />
        </div>
      </Modal>
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

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium mb-0.5" style={{ color: 'var(--color-muted)' }}>{label}</dt>
      <dd className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>{value}</dd>
    </div>
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
