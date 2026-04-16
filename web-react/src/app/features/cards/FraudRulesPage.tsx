// web-react/src/app/features/cards/FraudRulesPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { useFraudRules, useUpdateFraudRule } from './api/useCards'
import type { FraudRule } from './api/types'

const HARD_BLOCK_RULES = new Set(['CARD_EXPIRED', 'CARD_BLOCKED', 'PIN_RETRY_EXCEEDED'])

const RULE_DESCRIPTIONS: Record<string, string> = {
  VELOCITY_LIMIT:      'More than N transactions in Y minutes on same card',
  SINGLE_AMOUNT_LIMIT: 'Single transaction amount exceeds card daily limit',
  BLOCKED_COUNTRY:     'Transaction originates from a blocked country code',
  BLOCKED_MCC:         'Merchant Category Code is on the blocked list',
  DUPLICATE_TRANSACTION: 'Same amount + merchant within 2 minutes',
  CNP_DEBIT:           'Card-not-present transaction on a debit card',
  OUTSIDE_HOURS:       'Transaction outside permitted hours for card product',
  CARD_EXPIRED:        'Card expiry date (DE14) has passed',
  CARD_BLOCKED:        'Card status is BLOCKED or CANCELLED',
  PIN_RETRY_EXCEEDED:  'PIN retry counter ≥ 3',
}

function scoreVariant(w: number): { label: string; color: string } {
  if (w >= 70) return { label: 'DECLINE',  color: 'var(--color-error)' }
  if (w >= 30) return { label: 'STEP_UP',  color: 'var(--color-warning)' }
  return            { label: 'APPROVE',  color: 'var(--color-success)' }
}

export default function FraudRulesPage() {
  const { data, isLoading } = useFraudRules()
  const rules = (data as { data?: FraudRule[] } | undefined)?.data ?? []

  const [editing, setEditing]       = useState<FraudRule | null>(null)
  const [weightInput, setWeightInput] = useState('')
  const [paramsInput, setParamsInput] = useState('')
  const [paramsError, setParamsError] = useState('')

  const update = useUpdateFraudRule(editing?.id ?? '')

  function startEdit(r: FraudRule) {
    setEditing(r)
    setWeightInput(String(r.weight))
    setParamsInput(JSON.stringify(r.params, null, 2))
    setParamsError('')
  }

  async function saveWeight() {
    const w = parseInt(weightInput)
    if (isNaN(w) || w < 0 || w > 100) return
    await update.mutateAsync({ weight: w })
    setEditing(null)
  }

  async function saveParams() {
    try {
      const parsed = JSON.parse(paramsInput)
      setParamsError('')
      await update.mutateAsync({ params: parsed })
      setEditing(null)
    } catch { setParamsError('Invalid JSON') }
  }

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Fraud Rules"
        subtitle="Configure rule weights and thresholds for the authorization fraud engine"
      />

      {/* Score legend */}
      <div className="flex gap-4 mb-6">
        {[
          { range: '0 – 29', label: 'APPROVE', color: 'var(--color-success)', bg: 'var(--color-success-bg)' },
          { range: '30 – 69', label: 'STEP_UP', color: 'var(--color-warning)', bg: 'var(--color-warning-bg)' },
          { range: '70 – 100', label: 'DECLINE', color: 'var(--color-error)', bg: 'var(--color-error-bg)' },
        ].map(s => (
          <div key={s.label} className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium"
            style={{ background: s.bg, color: s.color }}>
            Score {s.range} → {s.label}
          </div>
        ))}
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium"
          style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)' }}>
          Weight 100 = Hard block (always decline)
        </div>
      </div>

      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
              {['Rule', 'Description', 'Weight', 'Decision', 'Hard Block', 'Enabled', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                  style={{ color: 'var(--color-muted)' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rules.map(r => {
              const isHard = HARD_BLOCK_RULES.has(r.ruleId)
              const sv = isHard ? { label: 'DECLINE', color: 'var(--color-error)' } : scoreVariant(r.weight)
              const isEditingThis = editing?.id === r.id

              return (
                <tr key={r.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                  <td className="px-4 py-3 font-mono text-xs font-semibold" style={{ color: 'var(--color-text)' }}>
                    {r.ruleId}
                  </td>
                  <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)', maxWidth: 260 }}>
                    {RULE_DESCRIPTIONS[r.ruleId] ?? '—'}
                  </td>
                  <td className="px-4 py-3 tabular-nums">
                    {isEditingThis ? (
                      <input type="number" min={0} max={100} value={weightInput}
                        onChange={e => setWeightInput(e.target.value)}
                        onBlur={() => saveWeight()}
                        onKeyDown={e => { if (e.key === 'Enter') saveWeight() }}
                        className="w-16 px-2 py-1 rounded text-sm outline-none"
                        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-primary)', color: 'var(--color-text)' }} />
                    ) : (
                      <span className="font-semibold text-sm" style={{ color: 'var(--color-text)' }}>{r.weight}</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-xs font-medium px-2 py-1 rounded-full"
                      style={{ background: `color-mix(in srgb, ${sv.color} 12%, transparent)`, color: sv.color }}>
                      {sv.label}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    {isHard && <span className="text-xs font-mono px-2 py-0.5 rounded" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>HARD</span>}
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={async () => {
                        try { await update.mutateAsync({ enabled: !r.enabled }) } catch { /* silent */ }
                      }}
                      className="relative inline-flex h-5 w-9 items-center rounded-full transition-colors"
                      style={{ background: r.enabled ? 'var(--color-success)' : 'var(--color-muted)' }}>
                      <span className="inline-block h-3.5 w-3.5 translate-x-1 rounded-full bg-white transition-transform"
                        style={{ transform: r.enabled ? 'translateX(18px)' : 'translateX(2px)' }} />
                    </button>
                  </td>
                  <td className="px-4 py-3">
                    <button onClick={() => isEditingThis ? setEditing(null) : startEdit(r)}
                      className="text-xs px-2 py-1 rounded"
                      style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>
                      {isEditingThis ? 'Done' : 'Edit params'}
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {/* Params editor panel */}
      {editing && (
        <div className="mt-4 rounded-xl p-5" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <p className="text-sm font-semibold mb-3" style={{ color: 'var(--color-text)' }}>
            Rule Parameters — <span className="font-mono">{editing.ruleId}</span>
          </p>
          {paramsError && <p className="text-xs mb-2" style={{ color: 'var(--color-error)' }}>{paramsError}</p>}
          <textarea rows={8} value={paramsInput} onChange={e => setParamsInput(e.target.value)}
            className="w-full px-3 py-2 rounded-lg text-sm font-mono outline-none resize-y"
            style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
          <div className="flex justify-end gap-3 mt-3">
            <button onClick={() => setEditing(null)} className="text-xs px-3 py-1.5 rounded-lg"
              style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={saveParams} disabled={update.isPending}
              className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}>
              {update.isPending ? 'Saving…' : 'Save Params'}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
