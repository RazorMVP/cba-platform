// web-react/src/app/features/cards/InterchangePage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import {
  useInterchangeRates, useCreateInterchangeRate, useDeleteInterchangeRate,
  useSchemeFees, useCreateSchemeFee, useDeleteSchemeFee,
} from './api/useCards'
import type { SchemeType, InterchangeRateRequest, SchemeFeeRequest, TxnType, Channel, FeeType } from './api/types'

const SCHEMES: SchemeType[] = ['VISA', 'MASTERCARD', 'VERVE', 'AFRIGO', 'UNION_PAY']
const TXN_TYPES: TxnType[]  = ['PURCHASE', 'CASH', 'REFUND']
const CHANNELS: Channel[]   = ['CARD_PRESENT', 'CNP']
const FEE_TYPES: FeeType[]  = ['ASSESSMENT', 'NETWORK', 'CROSS_BORDER', 'INTERNATIONAL_SERVICE']

type ActiveTab = 'rates' | 'fees'

function blankRate(): InterchangeRateRequest {
  return { scheme: 'VISA', cardType: 'DEBIT', transactionType: 'PURCHASE', channel: 'CARD_PRESENT', ratePercent: 0, fixedFee: 0, currencyCode: 'USD', effectiveFrom: '' }
}
function blankFee(): SchemeFeeRequest {
  return { scheme: 'VISA', feeType: 'ASSESSMENT', ratePercent: 0, fixedFee: 0, effectiveFrom: '' }
}

function schemeVariant(s: SchemeType): 'info' | 'success' | 'warning' | 'neutral' | 'error' {
  if (s === 'VISA')       return 'info'
  if (s === 'MASTERCARD') return 'success'
  if (s === 'VERVE')      return 'warning'
  if (s === 'AFRIGO')     return 'neutral'
  return 'error'
}

function DeleteBtn({ onDelete, isPending }: { onDelete: () => void; isPending: boolean }) {
  const [confirming, setConfirming] = useState(false)
  return confirming ? (
    <span className="flex gap-1">
      <button onClick={onDelete} disabled={isPending}
        className="text-xs px-2 py-1 rounded disabled:opacity-50"
        style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)', border: '1px solid var(--color-error)' }}>
        {isPending ? '…' : 'Confirm'}
      </button>
      <button onClick={() => setConfirming(false)} className="text-xs px-2 py-1 rounded"
        style={{ color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>Cancel</button>
    </span>
  ) : (
    <button onClick={() => setConfirming(true)} className="text-xs px-2 py-1 rounded"
      style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}>Delete</button>
  )
}

export default function InterchangePage() {
  const [tab, setTab]             = useState<ActiveTab>('rates')
  const [schemeFilter, setSchemeFilter] = useState<SchemeType | ''>('')

  // Rates
  const { data: rateData, isLoading: ratesLoading } = useInterchangeRates(schemeFilter || undefined)
  const rates = (rateData as { data?: import('./api/types').InterchangeRate[] } | undefined)?.data ?? []
  const createRate = useCreateInterchangeRate()
  const [showRateModal, setShowRateModal] = useState(false)
  const [rateForm, setRateForm]           = useState<InterchangeRateRequest>(blankRate())
  const [rateError, setRateError]         = useState('')

  // Fees
  const { data: feeData, isLoading: feesLoading } = useSchemeFees(schemeFilter || undefined)
  const fees = (feeData as { data?: import('./api/types').SchemeFee[] } | undefined)?.data ?? []
  const createFee = useCreateSchemeFee()
  const [showFeeModal, setShowFeeModal] = useState(false)
  const [feeForm, setFeeForm]           = useState<SchemeFeeRequest>(blankFee())
  const [feeError, setFeeError]         = useState('')

  async function saveRate() {
    if (!rateForm.effectiveFrom) { setRateError('Effective from date required'); return }
    setRateError('')
    try { await createRate.mutateAsync(rateForm); setShowRateModal(false); setRateForm(blankRate()) }
    catch (e) { setRateError(e instanceof Error ? e.message : 'Failed') }
  }

  async function saveFee() {
    if (!feeForm.effectiveFrom) { setFeeError('Effective from date required'); return }
    setFeeError('')
    try { await createFee.mutateAsync(feeForm); setShowFeeModal(false); setFeeForm(blankFee()) }
    catch (e) { setFeeError(e instanceof Error ? e.message : 'Failed') }
  }

  return (
    <div>
      <PageHeader
        title="Interchange"
        subtitle="Manage interchange rate tables and scheme assessment fees per card scheme"
        actions={
          <button
            onClick={() => tab === 'rates' ? (setRateForm(blankRate()), setRateError(''), setShowRateModal(true)) : (setFeeForm(blankFee()), setFeeError(''), setShowFeeModal(true))}
            className="px-4 py-2 rounded-lg text-sm font-medium text-white"
            style={{ background: 'var(--color-primary)' }}>
            {tab === 'rates' ? '+ Add Rate' : '+ Add Fee'}
          </button>
        }
      />

      {/* Tabs + scheme filter */}
      <div className="flex items-center justify-between mb-4 gap-4 flex-wrap">
        <div className="flex gap-1 border-b" style={{ borderColor: 'var(--color-border)' }}>
          {(['rates', 'fees'] as ActiveTab[]).map(t => (
            <button key={t} onClick={() => setTab(t)}
              className="px-4 py-2 text-sm font-medium capitalize border-b-2 -mb-px transition-colors"
              style={{ borderColor: tab === t ? 'var(--color-primary)' : 'transparent', color: tab === t ? 'var(--color-primary)' : 'var(--color-muted)' }}>
              {t === 'rates' ? 'Interchange Rates' : 'Scheme Fees'}
            </button>
          ))}
        </div>
        <select value={schemeFilter} onChange={e => setSchemeFilter(e.target.value as SchemeType | '')}
          className="px-3 py-2 rounded-lg text-sm outline-none"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
          <option value="">All Schemes</option>
          {SCHEMES.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>

      {/* Rates tab */}
      {tab === 'rates' && (
        ratesLoading ? (
          <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
        ) : (
          <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                  {['Scheme', 'Card Type', 'Txn Type', 'Channel', 'MCC', 'Rate %', 'Fixed Fee', 'Currency', 'Effective', 'Status', ''].map(h => (
                    <th key={h} className="px-3 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                      style={{ color: 'var(--color-muted)' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rates.length === 0 && (
                  <tr><td colSpan={11} className="px-4 py-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>No interchange rates configured.</td></tr>
                )}
                {rates.map(r => <RateRow key={r.id} rate={r} />)}
              </tbody>
            </table>
          </div>
        )
      )}

      {/* Fees tab */}
      {tab === 'fees' && (
        feesLoading ? (
          <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
        ) : (
          <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                  {['Scheme', 'Fee Type', 'Rate %', 'Fixed Fee', 'Effective From', 'Status', ''].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                      style={{ color: 'var(--color-muted)' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {fees.length === 0 && (
                  <tr><td colSpan={7} className="px-4 py-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>No scheme fees configured.</td></tr>
                )}
                {fees.map(f => <FeeRow key={f.id} fee={f} />)}
              </tbody>
            </table>
          </div>
        )
      )}

      {/* Add Rate Modal */}
      <Modal open={showRateModal} onClose={() => setShowRateModal(false)} title="Add Interchange Rate" size="md"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowRateModal(false)} className="px-4 py-2 text-sm rounded-lg"
              style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={saveRate} disabled={createRate.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}>
              {createRate.isPending ? 'Saving…' : 'Save'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-3">
          {rateError && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{rateError}</p>}
          <div className="grid grid-cols-2 gap-3">
            <IF label="Scheme" as="select" value={rateForm.scheme} onChange={e => setRateForm(p => ({ ...p, scheme: e.target.value as SchemeType }))}>
              {SCHEMES.map(s => <option key={s} value={s}>{s}</option>)}
            </IF>
            <IF label="Card Type" as="select" value={rateForm.cardType} onChange={e => setRateForm(p => ({ ...p, cardType: e.target.value as import('./api/types').CardType }))}>
              {['DEBIT','PREPAID','CREDIT'].map(t => <option key={t} value={t}>{t}</option>)}
            </IF>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <IF label="Transaction Type" as="select" value={rateForm.transactionType} onChange={e => setRateForm(p => ({ ...p, transactionType: e.target.value as TxnType }))}>
              {TXN_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
            </IF>
            <IF label="Channel" as="select" value={rateForm.channel} onChange={e => setRateForm(p => ({ ...p, channel: e.target.value as Channel }))}>
              {CHANNELS.map(c => <option key={c} value={c}>{c.replace(/_/g, ' ')}</option>)}
            </IF>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <NF label="Rate %" value={rateForm.ratePercent} onChange={v => setRateForm(p => ({ ...p, ratePercent: v }))} step={0.0001} />
            <NF label="Fixed Fee" value={rateForm.fixedFee} onChange={v => setRateForm(p => ({ ...p, fixedFee: v }))} step={0.0001} />
            <TF label="Currency" value={rateForm.currencyCode} onChange={e => setRateForm(p => ({ ...p, currencyCode: e.target.value.toUpperCase() }))} maxLength={3} />
          </div>
          <TF label="MCC Category (optional)" value={rateForm.mccCategory ?? ''} onChange={e => setRateForm(p => ({ ...p, mccCategory: e.target.value || undefined }))} />
          <TF label="Effective From (YYYY-MM-DD)" value={rateForm.effectiveFrom} onChange={e => setRateForm(p => ({ ...p, effectiveFrom: e.target.value }))} type="date" />
        </div>
      </Modal>

      {/* Add Fee Modal */}
      <Modal open={showFeeModal} onClose={() => setShowFeeModal(false)} title="Add Scheme Fee" size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowFeeModal(false)} className="px-4 py-2 text-sm rounded-lg"
              style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={saveFee} disabled={createFee.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}>
              {createFee.isPending ? 'Saving…' : 'Save'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-3">
          {feeError && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{feeError}</p>}
          <div className="grid grid-cols-2 gap-3">
            <IF label="Scheme" as="select" value={feeForm.scheme} onChange={e => setFeeForm(p => ({ ...p, scheme: e.target.value as SchemeType }))}>
              {SCHEMES.map(s => <option key={s} value={s}>{s}</option>)}
            </IF>
            <IF label="Fee Type" as="select" value={feeForm.feeType} onChange={e => setFeeForm(p => ({ ...p, feeType: e.target.value as FeeType }))}>
              {FEE_TYPES.map(f => <option key={f} value={f}>{f.replace(/_/g, ' ')}</option>)}
            </IF>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <NF label="Rate %" value={feeForm.ratePercent} onChange={v => setFeeForm(p => ({ ...p, ratePercent: v }))} step={0.0001} />
            <NF label="Fixed Fee" value={feeForm.fixedFee} onChange={v => setFeeForm(p => ({ ...p, fixedFee: v }))} step={0.0001} />
          </div>
          <TF label="Effective From (YYYY-MM-DD)" value={feeForm.effectiveFrom} onChange={e => setFeeForm(p => ({ ...p, effectiveFrom: e.target.value }))} type="date" />
        </div>
      </Modal>
    </div>
  )
}

function RateRow({ rate }: { rate: import('./api/types').InterchangeRate }) {
  const del = useDeleteInterchangeRate(rate.id)
  return (
    <tr style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
      <td className="px-3 py-2"><StatusBadge label={rate.scheme} variant={schemeVariant(rate.scheme)} /></td>
      <td className="px-3 py-2 text-xs" style={{ color: 'var(--color-text)' }}>{rate.cardType}</td>
      <td className="px-3 py-2 text-xs" style={{ color: 'var(--color-text)' }}>{rate.transactionType}</td>
      <td className="px-3 py-2 text-xs" style={{ color: 'var(--color-muted)' }}>{rate.channel}</td>
      <td className="px-3 py-2 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>{rate.mccCategory ?? 'All'}</td>
      <td className="px-3 py-2 tabular-nums text-sm" style={{ color: 'var(--color-text)' }}>{rate.ratePercent.toFixed(4)}%</td>
      <td className="px-3 py-2 tabular-nums text-sm" style={{ color: 'var(--color-text)' }}>{rate.fixedFee.toFixed(4)}</td>
      <td className="px-3 py-2 font-mono text-xs" style={{ color: 'var(--color-muted)' }}>{rate.currencyCode}</td>
      <td className="px-3 py-2 text-xs" style={{ color: 'var(--color-muted)' }}>{rate.effectiveFrom}</td>
      <td className="px-3 py-2"><StatusBadge label={rate.active ? 'ACTIVE' : 'INACTIVE'} variant={rate.active ? 'success' : 'neutral'} /></td>
      <td className="px-3 py-2"><DeleteBtn onDelete={() => del.mutate()} isPending={del.isPending} /></td>
    </tr>
  )
}

function FeeRow({ fee }: { fee: import('./api/types').SchemeFee }) {
  const del = useDeleteSchemeFee(fee.id)
  return (
    <tr style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
      <td className="px-4 py-2"><StatusBadge label={fee.scheme} variant={schemeVariant(fee.scheme)} /></td>
      <td className="px-4 py-2 text-xs" style={{ color: 'var(--color-text)' }}>{fee.feeType.replace(/_/g, ' ')}</td>
      <td className="px-4 py-2 tabular-nums text-sm" style={{ color: 'var(--color-text)' }}>{fee.ratePercent.toFixed(4)}%</td>
      <td className="px-4 py-2 tabular-nums text-sm" style={{ color: 'var(--color-text)' }}>{fee.fixedFee.toFixed(4)}</td>
      <td className="px-4 py-2 text-xs" style={{ color: 'var(--color-muted)' }}>{fee.effectiveFrom}</td>
      <td className="px-4 py-2"><StatusBadge label={fee.active ? 'ACTIVE' : 'INACTIVE'} variant={fee.active ? 'success' : 'neutral'} /></td>
      <td className="px-4 py-2"><DeleteBtn onDelete={() => del.mutate()} isPending={del.isPending} /></td>
    </tr>
  )
}

// Local form helpers
function TF({ label, ...props }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <input {...props} className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
    </div>
  )
}
function NF({ label, value, onChange, step = 1 }: { label: string; value: number; onChange: (v: number) => void; step?: number }) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <input type="number" min={0} step={step} value={value} onChange={e => onChange(parseFloat(e.target.value) || 0)}
        className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
    </div>
  )
}
function IF({ label, as: _as = 'select', children, ...props }: { label: string; as?: string; children: React.ReactNode } & React.SelectHTMLAttributes<HTMLSelectElement>) {
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
