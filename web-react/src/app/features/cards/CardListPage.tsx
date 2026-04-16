// web-react/src/app/features/cards/CardListPage.tsx
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useCards, useCardProducts, useIssueCard } from './api/useCards'
import type { CardType, CardStatus, CardIssueRequest } from './api/types'

const CARD_TYPE_OPTS: CardType[] = ['DEBIT', 'PREPAID', 'CREDIT']
const STATUS_OPTS: CardStatus[]   = ['ACTIVE', 'BLOCKED', 'EXPIRED', 'CANCELLED', 'ORDERED', 'ACTIVATION_PENDING']

function cardTypeVariant(t: CardType): 'info' | 'success' | 'warning' {
  if (t === 'DEBIT')   return 'info'
  if (t === 'CREDIT')  return 'success'
  return 'warning'
}

function statusVariant(s: CardStatus): 'success' | 'error' | 'neutral' | 'warning' {
  if (s === 'ACTIVE')             return 'success'
  if (s === 'BLOCKED')            return 'error'
  if (s === 'EXPIRED' || s === 'CANCELLED') return 'neutral'
  return 'warning'
}

function blankForm(): CardIssueRequest {
  return { customerId: '', productId: '', cardType: 'DEBIT', virtualFlag: false }
}

export default function CardListPage() {
  const navigate = useNavigate()
  const [typeFilter, setTypeFilter]     = useState<CardType | ''>('')
  const [statusFilter, setStatusFilter] = useState<CardStatus | ''>('')
  const [search, setSearch]             = useState('')

  const { data, isLoading } = useCards({
    cardType: typeFilter || undefined,
    status:   statusFilter || undefined,
  })
  const { data: prodData } = useCardProducts()
  const products = (prodData as { data: import('./api/types').CardProduct[] } | undefined)?.data ?? []

  const issueCard = useIssueCard()
  const [showModal, setShowModal] = useState(false)
  const [form, setForm]           = useState<CardIssueRequest>(blankForm())
  const [error, setError]         = useState('')

  const filtered = (Array.isArray(data) ? data : ((data as { data?: import('./api/types').Card[] } | undefined)?.data ?? []))
    .filter(c => {
      if (!search) return true
      const q = search.toLowerCase()
      return c.panLast4?.includes(q) || c.customerName?.toLowerCase().includes(q) || c.productName?.toLowerCase().includes(q)
    })

  async function save() {
    if (!form.customerId || !form.productId) { setError('Customer ID and product are required.'); return }
    setError('')
    try {
      await issueCard.mutateAsync(form)
      setShowModal(false)
      setForm(blankForm())
    } catch (err) { setError(err instanceof Error ? err.message : 'Issue failed') }
  }

  return (
    <div>
      <PageHeader title="Cards" actions={<Btn label="+ Issue Card" onClick={() => { setForm(blankForm()); setError(''); setShowModal(true) }} />} />

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3 mb-4">
        <input value={search} onChange={e => setSearch(e.target.value)}
          placeholder="Search PAN last 4, customer, product…"
          className="px-3 py-2 rounded-lg text-sm outline-none w-64"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
        <select value={typeFilter} onChange={e => setTypeFilter(e.target.value as CardType | '')}
          className="px-3 py-2 rounded-lg text-sm outline-none"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
          <option value="">All Types</option>
          {CARD_TYPE_OPTS.map(t => <option key={t} value={t}>{t}</option>)}
        </select>
        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value as CardStatus | '')}
          className="px-3 py-2 rounded-lg text-sm outline-none"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
          <option value="">All Statuses</option>
          {STATUS_OPTS.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>

      {isLoading ? (
        <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
      ) : (
        <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                {['PAN', 'Type', 'Product', 'Customer', 'Expiry', 'Status', 'Virtual', 'PIN Tries', ''].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                    style={{ color: 'var(--color-muted)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 && (
                <tr><td colSpan={9} className="px-4 py-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>No cards found.</td></tr>
              )}
              {filtered.map(c => (
                <tr key={c.id} onClick={() => navigate(`/cards/${c.id}`)}
                  className="cursor-pointer"
                  style={{ borderBottom: '1px solid var(--color-border)', height: 44, background: 'var(--bg-card)' }}
                  onMouseEnter={e => (e.currentTarget.style.background = 'var(--bg-subtle)')}
                  onMouseLeave={e => (e.currentTarget.style.background = 'var(--bg-card)')}>
                  <td className="px-4 py-2 font-mono text-xs" style={{ color: 'var(--color-text)' }}>
                    {c.panPrefix}••••{c.panLast4}
                  </td>
                  <td className="px-4 py-2"><StatusBadge label={c.cardType} variant={cardTypeVariant(c.cardType)} /></td>
                  <td className="px-4 py-2 text-sm" style={{ color: 'var(--color-text)' }}>{c.productName ?? '—'}</td>
                  <td className="px-4 py-2 text-sm" style={{ color: 'var(--color-muted)' }}>{c.customerName ?? c.customerId.slice(0, 8)}</td>
                  <td className="px-4 py-2 font-mono text-xs" style={{ color: 'var(--color-muted)' }}>{c.expiryDate}</td>
                  <td className="px-4 py-2"><StatusBadge label={c.status} variant={statusVariant(c.status)} /></td>
                  <td className="px-4 py-2 text-xs" style={{ color: c.virtualFlag ? 'var(--color-info)' : 'var(--color-muted)' }}>
                    {c.virtualFlag ? 'Virtual' : 'Physical'}
                  </td>
                  <td className="px-4 py-2 tabular-nums text-sm" style={{ color: c.pinRetryCount >= 3 ? 'var(--color-error)' : 'var(--color-text)' }}>
                    {c.pinRetryCount}/3
                  </td>
                  <td className="px-4 py-2 text-xs" style={{ color: 'var(--color-primary)' }}>View →</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Issue Card Modal */}
      <Modal open={showModal} onClose={() => setShowModal(false)} title="Issue Card" size="md"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowModal(false)} className="px-4 py-2 text-sm rounded-lg"
              style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={save} disabled={issueCard.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}>
              {issueCard.isPending ? 'Issuing…' : 'Issue Card'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-4">
          {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
          <Field label="Customer ID" value={form.customerId} onChange={e => setForm(p => ({ ...p, customerId: e.target.value }))} required />
          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Card Product</label>
            <select value={form.productId} onChange={e => setForm(p => ({ ...p, productId: e.target.value }))}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
              <option value="">Select product…</option>
              {products.map(p => <option key={p.id} value={p.id}>{p.name} ({p.cardType})</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Card Type</label>
            <select value={form.cardType} onChange={e => setForm(p => ({ ...p, cardType: e.target.value as CardType }))}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
              {CARD_TYPE_OPTS.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <label className="flex items-center gap-2 text-sm cursor-pointer" style={{ color: 'var(--color-text)' }}>
            <input type="checkbox" checked={form.virtualFlag}
              onChange={e => setForm(p => ({ ...p, virtualFlag: e.target.checked }))} className="w-4 h-4" />
            Issue as virtual card
          </label>
          <Field label="Linked Entity ID (account or loan, optional)"
            value={form.linkedEntityId ?? ''} onChange={e => setForm(p => ({ ...p, linkedEntityId: e.target.value || undefined }))} />
        </div>
      </Modal>
    </div>
  )
}

function Btn({ label, onClick }: { label: string; onClick: () => void }) {
  return <button onClick={onClick} className="px-4 py-2 rounded-lg text-sm font-medium text-white" style={{ background: 'var(--color-primary)' }}>{label}</button>
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
