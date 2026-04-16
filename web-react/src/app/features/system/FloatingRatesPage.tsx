// web-react/src/app/features/system/FloatingRatesPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import {
  useFloatingRates, useCreateFloatingRate, useUpdateFloatingRate, useDeleteFloatingRate,
} from './api/useSystem'
import type { FloatingRate, FloatingRatePeriod, CreateFloatingRateRequest } from './api/types'

// ── Rate period row inside accordion ─────────────────────────────────────────

function PeriodRow({ period }: { period: FloatingRatePeriod }) {
  return (
    <div className="flex items-center gap-4 px-4 py-2.5"
      style={{ borderTop: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
      <span className="text-xs tabular-nums w-28" style={{ color: 'var(--color-muted)' }}>
        {period.fromDate}
      </span>
      <span className="text-xs tabular-nums flex-1 font-mono" style={{ color: 'var(--color-text)' }}>
        {period.interestRate}%
      </span>
      {period.isDifferentialToBaseLendingRate && (
        <span className="text-xs px-2 py-0.5 rounded-full"
          style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
          Differential
        </span>
      )}
    </div>
  )
}

// ── Delete button — holds its own mutation ────────────────────────────────────

function DeleteRateButton({ id, onDeleted }: { id: string; onDeleted: () => void }) {
  const del = useDeleteFloatingRate(id)
  const [confirm, setConfirm] = useState(false)

  if (!confirm) {
    return (
      <button onClick={e => { e.stopPropagation(); setConfirm(true) }}
        className="text-xs px-2 py-1 rounded"
        style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}>
        Delete
      </button>
    )
  }

  return (
    <div className="flex gap-1" onClick={e => e.stopPropagation()}>
      <button
        onClick={async () => { await del.mutateAsync(); onDeleted() }}
        disabled={del.isPending}
        className="text-xs px-2 py-1 rounded disabled:opacity-60 text-white"
        style={{ background: 'var(--color-error)' }}
      >
        {del.isPending ? '…' : 'Confirm'}
      </button>
      <button onClick={() => setConfirm(false)} className="text-xs px-2 py-1 rounded"
        style={{ color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
        Cancel
      </button>
    </div>
  )
}

// ── Create / Edit modal ───────────────────────────────────────────────────────

const BLANK_PERIOD = (): FloatingRatePeriod => ({
  fromDate: '',
  interestRate: 0,
  isDifferentialToBaseLendingRate: false,
})

function RateModal({
  initial,
  onClose,
}: {
  initial: FloatingRate | null
  onClose: () => void
}) {
  const create = useCreateFloatingRate()
  const update = useUpdateFloatingRate(initial?.id ?? '')

  const [name,   setName]   = useState(initial?.name ?? '')
  const [isBase, setIsBase] = useState(initial?.isBaseLendingRate ?? false)
  const [isActive, setIsActive] = useState(initial?.isActive ?? true)
  const [periods, setPeriods] = useState<FloatingRatePeriod[]>(
    initial?.ratePeriods?.length ? [...initial.ratePeriods] : [BLANK_PERIOD()]
  )

  function setPeriodField<K extends keyof FloatingRatePeriod>(
    idx: number, key: K, val: FloatingRatePeriod[K]
  ) {
    setPeriods(prev => prev.map((p, i) => i === idx ? { ...p, [key]: val } : p))
  }

  function addPeriod() {
    setPeriods(prev => [...prev, BLANK_PERIOD()])
  }

  function removePeriod(idx: number) {
    setPeriods(prev => prev.filter((_, i) => i !== idx))
  }

  async function save() {
    const body: CreateFloatingRateRequest = {
      name, isBaseLendingRate: isBase, isActive,
      ratePeriods: periods.filter(p => p.fromDate),
    }
    if (initial) {
      await update.mutateAsync(body)
    } else {
      await create.mutateAsync(body)
    }
    onClose()
  }

  const isPending = create.isPending || update.isPending

  return (
    <div
      style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 50, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="rounded-xl p-6 w-full max-w-lg overflow-y-auto"
        style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', maxHeight: '90vh' }}>
        <h2 className="text-base font-semibold mb-4" style={{ color: 'var(--color-text)' }}>
          {initial ? 'Edit Floating Rate' : 'New Floating Rate'}
        </h2>

        {/* Name */}
        <div className="mb-3">
          <label className="block text-xs mb-1" style={{ color: 'var(--color-muted)' }}>Rate Name *</label>
          <input type="text" value={name} onChange={e => setName(e.target.value)}
            className="w-full px-3 py-2 rounded-lg text-sm outline-none"
            style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
        </div>

        {/* Flags */}
        <div className="flex gap-4 mb-4">
          <label className="flex items-center gap-2 text-xs cursor-pointer" style={{ color: 'var(--color-text)' }}>
            <input type="checkbox" checked={isBase} onChange={e => setIsBase(e.target.checked)} />
            Base Lending Rate
          </label>
          <label className="flex items-center gap-2 text-xs cursor-pointer" style={{ color: 'var(--color-text)' }}>
            <input type="checkbox" checked={isActive} onChange={e => setIsActive(e.target.checked)} />
            Active
          </label>
        </div>

        {/* Rate periods */}
        <div className="mb-4">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>Rate Periods</span>
            <button onClick={addPeriod} className="text-xs px-2 py-1 rounded"
              style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>
              + Add Period
            </button>
          </div>

          <div className="space-y-2">
            {periods.map((p, idx) => (
              <div key={idx} className="rounded-lg p-3 space-y-2"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)' }}>
                <div className="flex gap-2">
                  <div className="flex-1">
                    <label className="block text-xs mb-1" style={{ color: 'var(--color-muted)' }}>From Date</label>
                    <input type="date" value={p.fromDate}
                      onChange={e => setPeriodField(idx, 'fromDate', e.target.value)}
                      className="w-full px-2 py-1.5 rounded text-xs outline-none"
                      style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
                  </div>
                  <div className="flex-1">
                    <label className="block text-xs mb-1" style={{ color: 'var(--color-muted)' }}>Interest Rate (%)</label>
                    <input type="number" step="0.01" value={p.interestRate}
                      onChange={e => setPeriodField(idx, 'interestRate', Number(e.target.value))}
                      className="w-full px-2 py-1.5 rounded text-xs outline-none"
                      style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
                  </div>
                  {periods.length > 1 && (
                    <button onClick={() => removePeriod(idx)}
                      className="self-end text-xs px-2 py-1.5 rounded"
                      style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}>
                      ×
                    </button>
                  )}
                </div>
                <label className="flex items-center gap-2 text-xs cursor-pointer" style={{ color: 'var(--color-text)' }}>
                  <input type="checkbox"
                    checked={p.isDifferentialToBaseLendingRate}
                    onChange={e => setPeriodField(idx, 'isDifferentialToBaseLendingRate', e.target.checked)} />
                  Differential to Base Lending Rate
                </label>
              </div>
            ))}
          </div>
        </div>

        <div className="flex gap-2 justify-end">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
            Cancel
          </button>
          <button onClick={save} disabled={isPending || !name}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {isPending ? 'Saving…' : 'Save'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function FloatingRatesPage() {
  const { data, isLoading } = useFloatingRates()
  const rates: FloatingRate[] = (data as { data?: FloatingRate[] } | undefined)?.data ?? []

  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [modal, setModal] = useState<FloatingRate | null | 'new'>(null)

  function toggleExpand(id: string) {
    setExpanded(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Floating Rates"
        subtitle="Variable interest rate curves with effective date periods"
        actions={
          <button onClick={() => setModal('new')}
            className="text-xs px-3 py-1.5 rounded-lg text-white"
            style={{ background: 'var(--color-primary)' }}>
            + New Rate
          </button>
        }
      />

      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
        {rates.length === 0 && (
          <div className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
            No floating rates defined.
          </div>
        )}

        {rates.map((rate, idx) => (
          <div key={rate.id} style={{ borderTop: idx > 0 ? '1px solid var(--color-border)' : undefined }}>
            {/* Accordion header */}
            <div
              className="flex items-center gap-3 px-4 py-3 cursor-pointer select-none"
              style={{ background: 'var(--bg-card)' }}
              onClick={() => toggleExpand(rate.id)}
            >
              <span
                className="text-xs font-mono transition-transform"
                style={{ color: 'var(--color-muted)', display: 'inline-block', transform: expanded.has(rate.id) ? 'rotate(90deg)' : 'none' }}
              >▶</span>

              <span className="text-sm font-medium flex-1" style={{ color: 'var(--color-text)' }}>
                {rate.name}
              </span>

              <span className="text-xs tabular-nums" style={{ color: 'var(--color-muted)' }}>
                {rate.ratePeriods?.length ?? 0} period{rate.ratePeriods?.length !== 1 ? 's' : ''}
              </span>

              {rate.isBaseLendingRate && (
                <span className="text-xs px-2 py-0.5 rounded-full"
                  style={{ background: 'var(--color-primary)', color: '#fff' }}>
                  Base
                </span>
              )}

              {!rate.isActive && (
                <span className="text-xs px-2 py-0.5 rounded-full"
                  style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
                  Inactive
                </span>
              )}

              <button
                onClick={e => { e.stopPropagation(); setModal(rate) }}
                className="text-xs px-2 py-1 rounded"
                style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>
                Edit
              </button>

              <DeleteRateButton id={rate.id} onDeleted={() => {
                setExpanded(prev => { const n = new Set(prev); n.delete(rate.id); return n })
              }} />
            </div>

            {/* Rate periods */}
            {expanded.has(rate.id) && (
              <div style={{ borderTop: '1px solid var(--color-border)' }}>
                {(!rate.ratePeriods || rate.ratePeriods.length === 0) && (
                  <div className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>
                    No rate periods defined.
                  </div>
                )}
                <div className="px-4 py-2 grid grid-cols-3 gap-4"
                  style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                  {['From Date', 'Interest Rate', 'Differential'].map(h => (
                    <span key={h} className="text-xs font-semibold uppercase tracking-wider"
                      style={{ color: 'var(--color-muted)' }}>{h}</span>
                  ))}
                </div>
                {rate.ratePeriods?.map((p, i) => <PeriodRow key={i} period={p} />)}
              </div>
            )}
          </div>
        ))}
      </div>

      {modal !== null && (
        <RateModal
          initial={modal === 'new' ? null : modal}
          onClose={() => setModal(null)}
        />
      )}
    </div>
  )
}
