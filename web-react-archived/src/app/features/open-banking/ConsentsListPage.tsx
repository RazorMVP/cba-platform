// web-react/src/app/features/open-banking/ConsentsListPage.tsx
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { useConsents } from './api/useOpenBanking'
import type { Consent, ConsentType, ConsentStatus } from './api/types'

// ── Type + status helpers ─────────────────────────────────────────────────────

const TYPE_LABELS: Record<ConsentType, string> = {
  AISP:  'AISP',
  PISP:  'PISP',
  CBPII: 'CBPII',
}

const TYPE_COLORS: Record<ConsentType, { bg: string; color: string }> = {
  AISP:  { bg: '#eff6ff', color: '#1d4ed8' },
  PISP:  { bg: '#f0fdf4', color: '#15803d' },
  CBPII: { bg: '#fef3c7', color: '#92400e' },
}

const STATUS_VARIANT: Record<ConsentStatus, 'success' | 'warning' | 'neutral'> = {
  AUTHORISED:             'success',
  AWAITING_AUTHORISATION: 'warning',
  REVOKED:                'neutral',
}

const STATUS_LABEL: Record<ConsentStatus, string> = {
  AUTHORISED:             'Authorised',
  AWAITING_AUTHORISATION: 'Awaiting Authorisation',
  REVOKED:                'Revoked',
}

type TypeFilter = 'ALL' | ConsentType
type StatusFilter = 'ALL' | ConsentStatus

const TYPE_TABS: { key: TypeFilter; label: string }[] = [
  { key: 'ALL',   label: 'All' },
  { key: 'AISP',  label: 'AISP' },
  { key: 'PISP',  label: 'PISP' },
  { key: 'CBPII', label: 'CBPII' },
]

const STATUS_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL',                    label: 'All statuses' },
  { value: 'AUTHORISED',             label: 'Authorised' },
  { value: 'AWAITING_AUTHORISATION', label: 'Awaiting' },
  { value: 'REVOKED',                label: 'Revoked' },
]

// ── Scope chips ───────────────────────────────────────────────────────────────

function ScopeChips({ scopes }: { scopes: string[] }) {
  const MAX_VISIBLE = 3
  const visible = scopes.slice(0, MAX_VISIBLE)
  const overflow = scopes.length - MAX_VISIBLE

  return (
    <div className="flex flex-wrap gap-1">
      {visible.map(s => (
        <span key={s} className="text-xs px-1.5 py-0.5 rounded-full font-mono"
          style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
          {s}
        </span>
      ))}
      {overflow > 0 && (
        <span className="text-xs px-1.5 py-0.5 rounded-full"
          style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
          +{overflow}
        </span>
      )}
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function ConsentsListPage() {
  const { data, isLoading } = useConsents()
  const consents: Consent[] = (data as { data?: Consent[] } | undefined)?.data ?? []

  const [typeFilter,   setTypeFilter]   = useState<TypeFilter>('ALL')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')

  const filtered = consents.filter(c => {
    if (typeFilter !== 'ALL'   && c.type   !== typeFilter)   return false
    if (statusFilter !== 'ALL' && c.status !== statusFilter) return false
    return true
  })

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Open Banking Consents"
        subtitle="TPP consent records — AISP, PISP, and CBPII authorisations"
      />

      {/* Filters */}
      <div className="flex items-center gap-4 mb-5">
        {/* Type filter tabs */}
        <div className="flex gap-1 p-1 rounded-xl"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)' }}>
          {TYPE_TABS.map(t => (
            <button key={t.key} onClick={() => setTypeFilter(t.key)}
              className="text-xs px-4 py-1.5 rounded-lg transition-colors"
              style={{
                background: typeFilter === t.key ? 'var(--bg-card)' : 'transparent',
                color:      typeFilter === t.key ? 'var(--color-text)' : 'var(--color-muted)',
                fontWeight: typeFilter === t.key ? 600 : 400,
                border:     typeFilter === t.key ? '1px solid var(--color-border)' : '1px solid transparent',
              }}>
              {t.label}
            </button>
          ))}
        </div>

        {/* Status dropdown */}
        <select
          value={statusFilter}
          onChange={e => setStatusFilter(e.target.value as StatusFilter)}
          className="px-3 py-2 rounded-lg text-xs outline-none"
          style={{
            background: 'var(--bg-subtle)',
            border: '1px solid var(--color-border)',
            color: 'var(--color-text)',
          }}
        >
          {STATUS_OPTIONS.map(o => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>

        <span className="text-xs ml-auto" style={{ color: 'var(--color-muted)' }}>
          {filtered.length} consent{filtered.length !== 1 ? 's' : ''}
        </span>
      </div>

      {/* Table */}
      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
              {['Type', 'Client / TPP', 'Scopes', 'Status', 'Expires', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                  style={{ color: 'var(--color-muted)' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 && (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-xs"
                style={{ color: 'var(--color-muted)' }}>
                No consents found.
              </td></tr>
            )}
            {filtered.map(c => {
              const typeStyle = TYPE_COLORS[c.type]
              return (
                <tr key={c.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                  {/* Type badge */}
                  <td className="px-4 py-3">
                    <span className="text-xs font-semibold px-2 py-0.5 rounded-full"
                      style={{ background: typeStyle.bg, color: typeStyle.color }}>
                      {TYPE_LABELS[c.type]}
                    </span>
                  </td>

                  {/* Client */}
                  <td className="px-4 py-3">
                    <div className="text-xs font-mono" style={{ color: 'var(--color-text)' }}>
                      {c.tppName ?? c.clientId}
                    </div>
                    {c.tppName && (
                      <div className="text-xs font-mono mt-0.5" style={{ color: 'var(--color-muted)' }}>
                        {c.clientId}
                      </div>
                    )}
                  </td>

                  {/* Scopes */}
                  <td className="px-4 py-3">
                    <ScopeChips scopes={c.scopes} />
                  </td>

                  {/* Status */}
                  <td className="px-4 py-3">
                    <StatusBadge
                      label={STATUS_LABEL[c.status]}
                      variant={STATUS_VARIANT[c.status]}
                    />
                  </td>

                  {/* Expires */}
                  <td className="px-4 py-3 text-xs tabular-nums" style={{ color: 'var(--color-muted)' }}>
                    {c.expiresAt
                      ? new Date(c.expiresAt).toLocaleDateString()
                      : '—'}
                  </td>

                  {/* Action */}
                  <td className="px-4 py-3">
                    <Link to={`/open-banking/consents/${c.id}`}
                      className="text-xs px-2 py-1 rounded"
                      style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>
                      View
                    </Link>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
