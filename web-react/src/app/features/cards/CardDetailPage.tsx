// web-react/src/app/features/cards/CardDetailPage.tsx
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useCard, useCardLimits, useCardAuthorizations, useCardCommand, useUpdateCardLimits } from './api/useCards'
import type { CardLimitRequest } from './api/types'

type Tab = 'overview' | 'authorizations' | 'limits'

const RC_LABEL: Record<string, string> = {
  '00': 'Approved', '05': 'Do Not Honor', '51': 'Insufficient Funds',
  '54': 'Expired Card', '57': 'Not Permitted', '62': 'Restricted', '91': 'Issuer Unavailable',
}

export default function CardDetailPage() {
  const { id = '' } = useParams()
  const [tab, setTab] = useState<Tab>('overview')
  const [showLimitModal, setShowLimitModal] = useState(false)
  const [limitForm, setLimitForm]           = useState<CardLimitRequest | null>(null)
  const [error, setError]                   = useState('')

  const { data: cardData, isLoading } = useCard(id)
  const card = (cardData as { data?: import('./api/types').Card } | undefined)?.data

  const { data: limitsData } = useCardLimits(id)
  const limits = (limitsData as { data?: import('./api/types').CardLimit } | undefined)?.data

  const { data: authData } = useCardAuthorizations(id)
  const auths = (authData as { data?: import('./api/types').AuthorizationLog[] } | undefined)?.data ?? []

  const command     = useCardCommand(id)
  const updateLimit = useUpdateCardLimits(id)

  function openEditLimits() {
    if (!limits) return
    setLimitForm({
      dailyPurchaseLimit:  limits.dailyPurchaseLimit,
      dailyWithdrawalLimit: limits.dailyWithdrawalLimit,
      perTxnLimit:         limits.perTxnLimit,
      monthlyLimit:        limits.monthlyLimit,
      currencyCode:        limits.currencyCode,
    })
    setError('')
    setShowLimitModal(true)
  }

  async function saveLimit() {
    if (!limitForm) return
    setError('')
    try {
      await updateLimit.mutateAsync(limitForm)
      setShowLimitModal(false)
    } catch (err) { setError(err instanceof Error ? err.message : 'Save failed') }
  }

  async function doCommand(cmd: 'block' | 'unblock' | 'cancel' | 'activate') {
    if (!window.confirm(`${cmd.charAt(0).toUpperCase() + cmd.slice(1)} this card?`)) return
    try { await command.mutateAsync(cmd) } catch { /* silent */ }
  }

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
  if (!card) return <div className="p-8 text-sm" style={{ color: 'var(--color-error)' }}>Card not found.</div>

  const canBlock    = card.status === 'ACTIVE'
  const canUnblock  = card.status === 'BLOCKED'
  const canActivate = card.status === 'ACTIVATION_PENDING'
  const canCancel   = card.status !== 'CANCELLED' && card.status !== 'EXPIRED'

  return (
    <div>
      <PageHeader
        title={`${card.panPrefix}••••${card.panLast4}`}
        subtitle={card.cardType}
        actions={
          <div className="flex gap-2">
            {canActivate && <Cmd label="Activate" color="success" onClick={() => doCommand('activate')} />}
            {canBlock    && <Cmd label="Block"    color="error"   onClick={() => doCommand('block')}    />}
            {canUnblock  && <Cmd label="Unblock"  color="success" onClick={() => doCommand('unblock')}  />}
            {canCancel   && <Cmd label="Cancel"   color="error"   onClick={() => doCommand('cancel')}   />}
          </div>
        }
      />

      {/* Card summary strip */}
      <div className="rounded-xl p-4 mb-6 grid grid-cols-2 gap-4 sm:grid-cols-4"
        style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <Info label="Status"    value={<StatusBadge label={card.status} variant={card.status === 'ACTIVE' ? 'success' : card.status === 'BLOCKED' ? 'error' : 'neutral'} />} />
        <Info label="Type"      value={card.cardType} />
        <Info label="Expiry"    value={card.expiryDate} mono />
        <Info label="Virtual"   value={card.virtualFlag ? 'Yes' : 'No'} />
        <Info label="Customer"  value={card.customerName ?? card.customerId.slice(0, 8)} />
        <Info label="Product"   value={card.productName ?? '—'} />
        <Info label="PIN Tries" value={`${card.pinRetryCount} / 3`} mono warn={card.pinRetryCount >= 3} />
        <Info label="Issued"    value={new Date(card.createdAt).toLocaleDateString()} />
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-4 border-b" style={{ borderColor: 'var(--color-border)' }}>
        {(['overview', 'authorizations', 'limits'] as Tab[]).map(t => (
          <button key={t} onClick={() => setTab(t)}
            className="px-4 py-2 text-sm font-medium capitalize border-b-2 -mb-px transition-colors"
            style={{ borderColor: tab === t ? 'var(--color-primary)' : 'transparent', color: tab === t ? 'var(--color-primary)' : 'var(--color-muted)' }}>
            {t}
          </button>
        ))}
      </div>

      {/* ── Overview Tab ─────────────────────────────────────────────────────── */}
      {tab === 'overview' && (
        <div className="rounded-xl p-4" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <p className="text-sm font-semibold mb-3" style={{ color: 'var(--color-text)' }}>Card Details</p>
          <div className="grid grid-cols-2 gap-4">
            <Info label="Full PAN" value={`${card.panPrefix}••••${card.panLast4}`} mono />
            <Info label="Linked Account / Loan" value={card.linkedEntityId ?? '—'} mono />
            <Info label="Product ID" value={card.productId} mono />
            <Info label="Customer ID" value={card.customerId} mono />
          </div>
        </div>
      )}

      {/* ── Authorizations Tab ───────────────────────────────────────────────── */}
      {tab === 'authorizations' && (
        <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                {['Date', 'STAN', 'RRN', 'Amount', 'Merchant', 'Mode', 'RC', 'Score', 'Decision'].map(h => (
                  <th key={h} className="px-3 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                    style={{ color: 'var(--color-muted)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {auths.length === 0 && (
                <tr><td colSpan={9} className="px-4 py-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>No authorizations found.</td></tr>
              )}
              {auths.map(a => (
                <tr key={a.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                  <td className="px-3 py-2 text-xs" style={{ color: 'var(--color-muted)' }}>{new Date(a.createdAt).toLocaleDateString()}</td>
                  <td className="px-3 py-2 font-mono text-xs" style={{ color: 'var(--color-text)' }}>{a.stan}</td>
                  <td className="px-3 py-2 font-mono text-xs" style={{ color: 'var(--color-muted)' }}>{a.rrn}</td>
                  <td className="px-3 py-2 tabular-nums text-sm" style={{ color: 'var(--color-text)' }}>
                    {a.amount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                  </td>
                  <td className="px-3 py-2 text-xs" style={{ color: 'var(--color-text)' }}>{a.merchantName}</td>
                  <td className="px-3 py-2 text-xs" style={{ color: 'var(--color-muted)' }}>{a.entryMode}</td>
                  <td className="px-3 py-2">
                    <span className="font-mono text-xs px-1.5 py-0.5 rounded"
                      style={{ background: a.responseCode === '00' ? 'var(--color-success-bg)' : 'var(--color-error-bg)', color: a.responseCode === '00' ? 'var(--color-success)' : 'var(--color-error)' }}>
                      {a.responseCode} {RC_LABEL[a.responseCode] ?? ''}
                    </span>
                  </td>
                  <td className="px-3 py-2 tabular-nums text-sm" style={{ color: a.fraudScore >= 70 ? 'var(--color-error)' : a.fraudScore >= 30 ? 'var(--color-warning)' : 'var(--color-success)' }}>
                    {a.fraudScore}
                  </td>
                  <td className="px-3 py-2">
                    <StatusBadge label={a.decision} variant={a.decision === 'APPROVE' ? 'success' : a.decision === 'DECLINE' ? 'error' : 'warning'} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Limits Tab ───────────────────────────────────────────────────────── */}
      {tab === 'limits' && (
        <div className="rounded-xl p-5" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <div className="flex items-center justify-between mb-4">
            <p className="text-sm font-semibold" style={{ color: 'var(--color-text)' }}>Spending Limits</p>
            <button onClick={openEditLimits} className="text-xs px-3 py-1.5 rounded-lg"
              style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Edit Limits</button>
          </div>
          {!limits ? (
            <p className="text-sm" style={{ color: 'var(--color-muted)' }}>No limits configured.</p>
          ) : (
            <div className="grid grid-cols-2 gap-4">
              <Info label="Daily Purchase" value={limits.dailyPurchaseLimit.toLocaleString()} mono />
              <Info label="Daily Withdrawal" value={limits.dailyWithdrawalLimit.toLocaleString()} mono />
              <Info label="Per Transaction" value={limits.perTxnLimit.toLocaleString()} mono />
              <Info label="Monthly" value={limits.monthlyLimit.toLocaleString()} mono />
              <Info label="Currency" value={limits.currencyCode} />
            </div>
          )}
        </div>
      )}

      {/* Edit Limits Modal */}
      <Modal open={showLimitModal} onClose={() => setShowLimitModal(false)} title="Edit Card Limits" size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowLimitModal(false)} className="px-4 py-2 text-sm rounded-lg"
              style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={saveLimit} disabled={updateLimit.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}>
              {updateLimit.isPending ? 'Saving…' : 'Save'}
            </button>
          </div>
        }>
        {limitForm && (
          <div className="p-6 space-y-3">
            {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
            {(['dailyPurchaseLimit', 'dailyWithdrawalLimit', 'perTxnLimit', 'monthlyLimit'] as const).map(f => (
              <LField key={f} label={f.replace(/([A-Z])/g, ' $1').trim()}
                value={limitForm[f]} onChange={v => setLimitForm(p => p ? ({ ...p, [f]: v }) : p)} />
            ))}
            <div>
              <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Currency</label>
              <input value={limitForm.currencyCode} onChange={e => setLimitForm(p => p ? ({ ...p, currencyCode: e.target.value }) : p)}
                className="w-full px-3 py-2 rounded-lg text-sm outline-none"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}

function Info({ label, value, mono = false, warn = false }: { label: string; value: React.ReactNode; mono?: boolean; warn?: boolean }) {
  return (
    <div>
      <p className="text-xs mb-0.5" style={{ color: 'var(--color-muted)' }}>{label}</p>
      <p className={`text-sm font-medium ${mono ? 'font-mono' : ''}`}
        style={{ color: warn ? 'var(--color-error)' : 'var(--color-text)' }}>{value}</p>
    </div>
  )
}
function Cmd({ label, color, onClick }: { label: string; color: 'success' | 'error'; onClick: () => void }) {
  return (
    <button onClick={onClick} className="text-xs px-3 py-1.5 rounded-lg"
      style={{ border: `1px solid var(--color-${color})`, color: `var(--color-${color})` }}>
      {label}
    </button>
  )
}
function LField({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1 capitalize" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <input type="number" min={0} value={value} onChange={e => onChange(parseFloat(e.target.value) || 0)}
        className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
    </div>
  )
}
