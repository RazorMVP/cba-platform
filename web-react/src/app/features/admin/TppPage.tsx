// web-react/src/app/features/admin/TppPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import {
  useTpps,
  useRegisterTpp,
  useActivateTpp,
  useRevokeTpp,
} from './api/useAdmin'
import type { TppRegistration, TppStatus, RegisterTppRequest } from './api/types'

const AVAILABLE_SCOPES = [
  'accounts_read', 'balances_read', 'transactions_read',
  'card_read', 'card_balances_read', 'card_transactions_read',
  'payments_write', 'fundsconfirmation',
]

function statusVariant(s: TppStatus): 'success' | 'neutral' | 'warning' {
  if (s === 'ACTIVE')  return 'success'
  if (s === 'REVOKED') return 'neutral'
  return 'warning'
}

// ── Activate / Revoke buttons (hooks at top level per row) ────────────────────

function TppActions({ tpp, onDone }: { tpp: TppRegistration; onDone: () => void }) {
  const activate = useActivateTpp(tpp.id)
  const revoke   = useRevokeTpp(tpp.id)

  if (tpp.status === 'REVOKED') return null

  return (
    <div className="flex gap-1.5">
      {tpp.status === 'PENDING' && (
        <button
          onClick={async () => { await activate.mutateAsync(); onDone() }}
          disabled={activate.isPending}
          className="text-xs px-2 py-1 rounded disabled:opacity-60 text-white"
          style={{ background: 'var(--color-success)' }}
        >
          {activate.isPending ? '…' : 'Activate'}
        </button>
      )}
      <button
        onClick={async () => { await revoke.mutateAsync(); onDone() }}
        disabled={revoke.isPending}
        className="text-xs px-2 py-1 rounded disabled:opacity-60"
        style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}
      >
        {revoke.isPending ? '…' : 'Revoke'}
      </button>
    </div>
  )
}

// ── Register modal ────────────────────────────────────────────────────────────

function RegisterTppModal({ onClose }: { onClose: () => void }) {
  const register = useRegisterTpp()

  const [form, setForm] = useState<RegisterTppRequest>({
    name:              '',
    clientId:          '',
    country:           '',
    allowedScopes:     [],
    certificateExpiry: '',
  })

  function toggleScope(scope: string) {
    setForm(prev => ({
      ...prev,
      allowedScopes: prev.allowedScopes.includes(scope)
        ? prev.allowedScopes.filter(s => s !== scope)
        : [...prev.allowedScopes, scope],
    }))
  }

  async function save() {
    const payload = {
      ...form,
      certificateExpiry: form.certificateExpiry || undefined,
    }
    await register.mutateAsync(payload)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.5)' }}>
      <div className="w-full max-w-lg rounded-2xl p-6 overflow-y-auto" style={{ background: 'var(--bg-card)', maxHeight: '90vh' }}>
        <h2 className="text-base font-semibold mb-4" style={{ color: 'var(--color-text)' }}>
          Register TPP
        </h2>

        <div className="space-y-3">
          {([
            { label: 'Provider Name', key: 'name'     as const, placeholder: 'My Open Banking App' },
            { label: 'Client ID',     key: 'clientId' as const, placeholder: 'client_12345'         },
            { label: 'Country',       key: 'country'  as const, placeholder: 'GB'                   },
          ]).map(({ label, key, placeholder }) => (
            <div key={key}>
              <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>{label}</label>
              <input
                type="text"
                value={form[key]}
                onChange={e => setForm(prev => ({ ...prev, [key]: e.target.value }))}
                placeholder={placeholder}
                className="w-full px-2 py-1.5 rounded text-sm outline-none"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
              />
            </div>
          ))}

          <div>
            <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>
              Certificate Expiry (optional)
            </label>
            <input
              type="date"
              value={form.certificateExpiry ?? ''}
              onChange={e => setForm(prev => ({ ...prev, certificateExpiry: e.target.value }))}
              className="w-full px-2 py-1.5 rounded text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
            />
          </div>

          {/* Scopes */}
          <div>
            <label className="block text-xs mb-2 font-medium" style={{ color: 'var(--color-muted)' }}>
              Allowed Scopes ({form.allowedScopes.length} selected)
            </label>
            <div className="flex flex-wrap gap-2">
              {AVAILABLE_SCOPES.map(scope => (
                <button
                  key={scope}
                  onClick={() => toggleScope(scope)}
                  className="text-xs px-2.5 py-1 rounded-full"
                  style={{
                    background: form.allowedScopes.includes(scope) ? 'var(--color-primary)' : 'var(--bg-subtle)',
                    color:      form.allowedScopes.includes(scope) ? '#fff' : 'var(--color-muted)',
                    border:     '1px solid var(--color-border)',
                  }}
                >
                  {scope.replace(/_/g, ' ')}
                </button>
              ))}
            </div>
          </div>
        </div>

        {register.isError && (
          <p className="text-xs mt-3" style={{ color: 'var(--color-error)' }}>Failed to register TPP.</p>
        )}

        <div className="flex justify-end gap-3 mt-5">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button
            onClick={save}
            disabled={register.isPending}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}
          >
            {register.isPending ? 'Registering…' : 'Register TPP'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function TppPage() {
  const { data, isLoading, refetch } = useTpps()
  const tpps: TppRegistration[] = (data as { data?: TppRegistration[] } | undefined)?.data ?? []

  const [registering, setRegistering] = useState(false)
  const [search,      setSearch]      = useState('')
  const [statusFilter, setStatusFilter] = useState<TppStatus | ''>('')

  const filtered = tpps.filter(t => {
    if (statusFilter && t.status !== statusFilter) return false
    if (search) {
      const q = search.toLowerCase()
      return t.name.toLowerCase().includes(q) || t.clientId.toLowerCase().includes(q) || t.country.toLowerCase().includes(q)
    }
    return true
  })

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="TPP Management"
        subtitle="Open Banking third-party provider registry"
        actions={
          <button
            onClick={() => setRegistering(true)}
            className="text-xs px-3 py-1.5 rounded-lg text-white"
            style={{ background: 'var(--color-primary)' }}
          >
            + Register TPP
          </button>
        }
      />

      {/* Filters */}
      <div className="flex gap-3 mb-4">
        <input
          type="text"
          placeholder="Search by name, client ID, country…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="px-3 py-2 rounded-lg text-sm outline-none"
          style={{
            background: 'var(--bg-subtle)',
            border: '1px solid var(--color-border)',
            color: 'var(--color-text)',
            width: 280,
          }}
        />
        <select
          value={statusFilter}
          onChange={e => setStatusFilter(e.target.value as TppStatus | '')}
          className="px-3 py-2 rounded-lg text-sm outline-none"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
        >
          <option value="">All statuses</option>
          <option value="PENDING">Pending</option>
          <option value="ACTIVE">Active</option>
          <option value="REVOKED">Revoked</option>
        </select>
      </div>

      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
              {['Name', 'Client ID', 'Country', 'Scopes', 'Cert Expiry', 'Status', 'Registered', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                  style={{ color: 'var(--color-muted)' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 && (
              <tr>
                <td colSpan={8} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                  No TPPs found.
                </td>
              </tr>
            )}
            {filtered.map(t => (
              <tr key={t.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                <td className="px-4 py-3 font-medium text-sm" style={{ color: 'var(--color-text)' }}>{t.name}</td>
                <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>{t.clientId}</td>
                <td className="px-4 py-3 text-xs font-semibold" style={{ color: 'var(--color-muted)' }}>{t.country}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1">
                    {t.allowedScopes.slice(0, 3).map(s => (
                      <span key={s} className="text-xs px-2 py-0.5 rounded-full"
                        style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
                        {s.replace(/_/g, ' ')}
                      </span>
                    ))}
                    {t.allowedScopes.length > 3 && (
                      <span className="text-xs px-2 py-0.5 rounded-full"
                        style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
                        +{t.allowedScopes.length - 3}
                      </span>
                    )}
                  </div>
                </td>
                <td className="px-4 py-3 text-xs tabular-nums" style={{ color: 'var(--color-muted)' }}>
                  {t.certificateExpiry
                    ? new Date(t.certificateExpiry).toLocaleDateString()
                    : '—'}
                </td>
                <td className="px-4 py-3">
                  <StatusBadge label={t.status} variant={statusVariant(t.status)} />
                </td>
                <td className="px-4 py-3 text-xs tabular-nums" style={{ color: 'var(--color-muted)' }}>
                  {new Date(t.registeredAt).toLocaleDateString()}
                </td>
                <td className="px-4 py-3">
                  <TppActions tpp={t} onDone={() => refetch()} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {registering && <RegisterTppModal onClose={() => setRegistering(false)} />}
    </div>
  )
}
