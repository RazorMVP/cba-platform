// web-react/src/app/features/cards/BinManagementPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useBinRanges, useCreateBinRange, useUpdateBinRange, useDeleteBinRange } from './api/useCards'
import type { BinRange, BinRangeRequest, SchemeType, CardType } from './api/types'

const SCHEMES: SchemeType[]   = ['VISA', 'MASTERCARD', 'VERVE', 'AFRIGO', 'UNION_PAY']
const CARD_TYPES: CardType[]  = ['DEBIT', 'PREPAID', 'CREDIT']

function schemeVariant(s: SchemeType): 'info' | 'success' | 'warning' | 'neutral' | 'error' {
  if (s === 'VISA')        return 'info'
  if (s === 'MASTERCARD')  return 'success'
  if (s === 'VERVE')       return 'warning'
  if (s === 'AFRIGO')      return 'neutral'
  return 'error'   // UNION_PAY
}

function blankForm(): BinRangeRequest {
  return { binStart: '', binEnd: '', scheme: 'VISA', cardType: 'DEBIT' }
}

function DeleteRow({ bin }: { bin: BinRange }) {
  const del = useDeleteBinRange(bin.id)
  const [confirming, setConfirming] = useState(false)
  return confirming ? (
    <span className="flex gap-1">
      <button onClick={() => del.mutate()} disabled={del.isPending}
        className="text-xs px-2 py-1 rounded disabled:opacity-50"
        style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)', border: '1px solid var(--color-error)' }}>
        {del.isPending ? '…' : 'Confirm'}
      </button>
      <button onClick={() => setConfirming(false)} className="text-xs px-2 py-1 rounded"
        style={{ color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>Cancel</button>
    </span>
  ) : (
    <button onClick={() => setConfirming(true)} className="text-xs px-2 py-1 rounded"
      style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}>Delete</button>
  )
}

export default function BinManagementPage() {
  const { data, isLoading } = useBinRanges()
  const bins = (data as { data?: BinRange[] } | undefined)?.data ?? []

  const create  = useCreateBinRange()
  const [editing, setEditing]   = useState<BinRange | null>(null)
  const update  = useUpdateBinRange(editing?.id ?? '')

  const [showModal, setShowModal] = useState(false)
  const [form, setForm]           = useState<BinRangeRequest>(blankForm())
  const [error, setError]         = useState('')

  function openCreate() { setEditing(null); setForm(blankForm()); setError(''); setShowModal(true) }
  function openEdit(b: BinRange) {
    setEditing(b)
    setForm({ binStart: b.binStart, binEnd: b.binEnd, scheme: b.scheme, cardType: b.cardType, productType: b.productType, countryCode: b.countryCode, currencyCode: b.currencyCode })
    setError(''); setShowModal(true)
  }

  async function save() {
    if (!form.binStart || !form.binEnd) { setError('BIN start and end are required'); return }
    if (form.binStart.length < 6 || form.binEnd.length < 6) { setError('BIN must be 6–8 digits'); return }
    setError('')
    try {
      if (editing) { await update.mutateAsync(form) } else { await create.mutateAsync(form) }
      setShowModal(false)
    } catch (e) { setError(e instanceof Error ? e.message : 'Save failed') }
  }

  const isPending = create.isPending || update.isPending

  return (
    <div>
      <PageHeader
        title="BIN Management"
        subtitle="Register 6 and 8-digit Bank Identification Number ranges per scheme"
        actions={
          <button onClick={openCreate}
            className="px-4 py-2 rounded-lg text-sm font-medium text-white"
            style={{ background: 'var(--color-primary)' }}>+ Add BIN Range</button>
        }
      />

      {isLoading ? (
        <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
      ) : (
        <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                {['BIN Start', 'BIN End', 'Scheme', 'Card Type', 'Product', 'Country', 'Currency', 'Status', ''].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                    style={{ color: 'var(--color-muted)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {bins.length === 0 && (
                <tr><td colSpan={9} className="px-4 py-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>No BIN ranges registered.</td></tr>
              )}
              {bins.map(b => (
                <tr key={b.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                  <td className="px-4 py-2 font-mono text-xs font-semibold" style={{ color: 'var(--color-text)' }}>{b.binStart}</td>
                  <td className="px-4 py-2 font-mono text-xs font-semibold" style={{ color: 'var(--color-text)' }}>{b.binEnd}</td>
                  <td className="px-4 py-2"><StatusBadge label={b.scheme} variant={schemeVariant(b.scheme)} /></td>
                  <td className="px-4 py-2">
                    <StatusBadge label={b.cardType} variant={b.cardType === 'DEBIT' ? 'info' : b.cardType === 'CREDIT' ? 'success' : 'warning'} />
                  </td>
                  <td className="px-4 py-2 text-xs" style={{ color: 'var(--color-muted)' }}>{b.productType ?? '—'}</td>
                  <td className="px-4 py-2 font-mono text-xs" style={{ color: 'var(--color-muted)' }}>{b.countryCode ?? '—'}</td>
                  <td className="px-4 py-2 font-mono text-xs" style={{ color: 'var(--color-muted)' }}>{b.currencyCode ?? '—'}</td>
                  <td className="px-4 py-2">
                    <StatusBadge label={b.active ? 'ACTIVE' : 'INACTIVE'} variant={b.active ? 'success' : 'neutral'} />
                  </td>
                  <td className="px-4 py-2">
                    <div className="flex gap-2">
                      <button onClick={() => openEdit(b)} className="text-xs px-2 py-1 rounded"
                        style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>Edit</button>
                      {b.active && <DeleteRow bin={b} />}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={showModal} onClose={() => setShowModal(false)}
        title={editing ? 'Edit BIN Range' : 'Add BIN Range'} size="md"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowModal(false)} className="px-4 py-2 text-sm rounded-lg"
              style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={save} disabled={isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}>
              {isPending ? 'Saving…' : 'Save'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-3">
          {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
          <div className="grid grid-cols-2 gap-3">
            <BF label="BIN Start (6–8 digits)" value={form.binStart} onChange={e => setForm(p => ({ ...p, binStart: e.target.value.replace(/\D/g, '') }))} maxLength={8} placeholder="40000000" />
            <BF label="BIN End (6–8 digits)" value={form.binEnd} onChange={e => setForm(p => ({ ...p, binEnd: e.target.value.replace(/\D/g, '') }))} maxLength={8} placeholder="40999999" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Sel label="Scheme" value={form.scheme} onChange={e => setForm(p => ({ ...p, scheme: e.target.value as SchemeType }))}>
              {SCHEMES.map(s => <option key={s} value={s}>{s}</option>)}
            </Sel>
            <Sel label="Card Type" value={form.cardType} onChange={e => setForm(p => ({ ...p, cardType: e.target.value as CardType }))}>
              {CARD_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
            </Sel>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <BF label="Product Type (optional)" value={form.productType ?? ''} onChange={e => setForm(p => ({ ...p, productType: e.target.value || undefined }))} />
            <BF label="Country Code (ISO 3166)" value={form.countryCode ?? ''} onChange={e => setForm(p => ({ ...p, countryCode: e.target.value.toUpperCase() || undefined }))} maxLength={2} placeholder="NG" />
            <BF label="Currency Code (ISO 4217)" value={form.currencyCode ?? ''} onChange={e => setForm(p => ({ ...p, currencyCode: e.target.value.toUpperCase() || undefined }))} maxLength={3} placeholder="NGN" />
          </div>
        </div>
      </Modal>
    </div>
  )
}

function BF({ label, ...props }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <input {...props} className="w-full px-3 py-2 rounded-lg text-sm outline-none font-mono"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
    </div>
  )
}

function Sel({ label, children, ...props }: { label: string; children: React.ReactNode } & React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <select {...props} className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
        {children}
      </select>
    </div>
  )
}
