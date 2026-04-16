// web-react/src/app/features/cards/CardProductsPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useCardProducts, useCreateCardProduct } from './api/useCards'
import type { CardType, CardProductRequest } from './api/types'

const CARD_TYPES: CardType[] = ['DEBIT', 'PREPAID', 'CREDIT']

function blankForm(): CardProductRequest {
  return { name: '', cardType: 'DEBIT', binRangeStart: '', binRangeEnd: '', defaultDailyLimit: 100000 }
}

export default function CardProductsPage() {
  const { data, isLoading } = useCardProducts()
  const products = (data as { data?: import('./api/types').CardProduct[] } | undefined)?.data ?? []

  const create = useCreateCardProduct()
  const [showModal, setShowModal] = useState(false)
  const [form, setForm]           = useState<CardProductRequest>(blankForm())
  const [error, setError]         = useState('')

  async function save() {
    if (!form.name || !form.binRangeStart || !form.binRangeEnd) {
      setError('Name and BIN range are required.'); return
    }
    setError('')
    try {
      await create.mutateAsync(form)
      setShowModal(false)
      setForm(blankForm())
    } catch (err) { setError(err instanceof Error ? err.message : 'Create failed') }
  }

  return (
    <div>
      <PageHeader
        title="Card Products"
        subtitle="Define card product templates with BIN ranges and default limits"
        actions={
          <button onClick={() => { setForm(blankForm()); setError(''); setShowModal(true) }}
            className="px-4 py-2 rounded-lg text-sm font-medium text-white"
            style={{ background: 'var(--color-primary)' }}>+ New Product</button>
        }
      />

      {isLoading ? (
        <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
      ) : (
        <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                {['Name', 'Type', 'BIN Range', 'Default Daily Limit', 'Status', 'Created'].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                    style={{ color: 'var(--color-muted)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {products.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>No card products found.</td></tr>
              )}
              {products.map(p => (
                <tr key={p.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                  <td className="px-4 py-3 text-sm font-medium" style={{ color: 'var(--color-text)' }}>{p.name}</td>
                  <td className="px-4 py-3">
                    <StatusBadge label={p.cardType} variant={p.cardType === 'DEBIT' ? 'info' : p.cardType === 'CREDIT' ? 'success' : 'warning'} />
                  </td>
                  <td className="px-4 py-3 font-mono text-xs" style={{ color: 'var(--color-muted)' }}>
                    {p.binRangeStart} – {p.binRangeEnd}
                  </td>
                  <td className="px-4 py-3 tabular-nums text-sm" style={{ color: 'var(--color-text)' }}>
                    {p.defaultDailyLimit.toLocaleString()}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge label={p.active ? 'ACTIVE' : 'INACTIVE'} variant={p.active ? 'success' : 'neutral'} />
                  </td>
                  <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>
                    {new Date(p.createdAt).toLocaleDateString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={showModal} onClose={() => setShowModal(false)} title="New Card Product" size="md"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowModal(false)} className="px-4 py-2 text-sm rounded-lg"
              style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={save} disabled={create.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}>
              {create.isPending ? 'Creating…' : 'Create'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-4">
          {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
          <F label="Product Name" value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} required />
          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Card Type</label>
            <select value={form.cardType} onChange={e => setForm(p => ({ ...p, cardType: e.target.value as CardType }))}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
              {CARD_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <F label="BIN Range Start (8 digits)" value={form.binRangeStart} onChange={e => setForm(p => ({ ...p, binRangeStart: e.target.value }))} maxLength={8} required />
            <F label="BIN Range End (8 digits)" value={form.binRangeEnd} onChange={e => setForm(p => ({ ...p, binRangeEnd: e.target.value }))} maxLength={8} required />
          </div>
          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Default Daily Limit</label>
            <input type="number" min={0} value={form.defaultDailyLimit}
              onChange={e => setForm(p => ({ ...p, defaultDailyLimit: parseFloat(e.target.value) || 0 }))}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
          </div>
        </div>
      </Modal>
    </div>
  )
}

function F({ label, ...props }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <input {...props} className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
    </div>
  )
}
