// web-react/src/app/features/cards/ApiKeysPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useApiKeys, useCreateApiKey, useRevokeApiKey } from './api/useCards'
import type { ApiKey, ApiKeyRequest } from './api/types'

const ALL_SCOPES = [
  'cards:read', 'cards:write', 'authorizations:read',
  'limits:write', 'disputes:read', 'disputes:write',
  'analytics:read', 'webhooks:write',
]

function blankForm(): ApiKeyRequest { return { name: '', scopes: [] } }

function RevealedKey({ value }: { value: string }) {
  const [copied, setCopied] = useState(false)
  function copy() {
    navigator.clipboard.writeText(value).then(() => { setCopied(true); setTimeout(() => setCopied(false), 2000) })
  }
  return (
    <div className="mt-4 p-4 rounded-lg" style={{ background: 'var(--color-success-bg)', border: '1px solid var(--color-success)' }}>
      <p className="text-xs font-semibold mb-1" style={{ color: 'var(--color-success)' }}>
        API key created — copy it now. It will not be shown again.
      </p>
      <div className="flex items-center gap-2 mt-2">
        <code className="flex-1 text-xs break-all font-mono p-2 rounded"
          style={{ background: 'var(--bg-subtle)', color: 'var(--color-text)' }}>
          {value}
        </code>
        <button onClick={copy} className="shrink-0 text-xs px-3 py-1.5 rounded-lg"
          style={{ border: '1px solid var(--color-success)', color: 'var(--color-success)' }}>
          {copied ? 'Copied!' : 'Copy'}
        </button>
      </div>
    </div>
  )
}

function RevokeRow({ apiKey }: { apiKey: ApiKey }) {
  const revoke = useRevokeApiKey(apiKey.id)
  const [confirming, setConfirming] = useState(false)
  return confirming ? (
    <span className="flex gap-1">
      <button onClick={() => revoke.mutate()} disabled={revoke.isPending}
        className="text-xs px-2 py-1 rounded disabled:opacity-50"
        style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)', border: '1px solid var(--color-error)' }}>
        {revoke.isPending ? '…' : 'Confirm'}
      </button>
      <button onClick={() => setConfirming(false)} className="text-xs px-2 py-1 rounded"
        style={{ color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>Cancel</button>
    </span>
  ) : (
    <button onClick={() => setConfirming(true)} className="text-xs px-2 py-1 rounded"
      style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}>Revoke</button>
  )
}

export default function ApiKeysPage() {
  const { data, isLoading } = useApiKeys()
  const keys = (data as { data?: ApiKey[] } | undefined)?.data ?? []

  const create = useCreateApiKey()
  const [showModal, setShowModal] = useState(false)
  const [form, setForm]           = useState<ApiKeyRequest>(blankForm())
  const [revealed, setRevealed]   = useState<string | null>(null)
  const [error, setError]         = useState('')

  function toggleScope(scope: string) {
    setForm(p => ({
      ...p,
      scopes: p.scopes.includes(scope) ? p.scopes.filter(s => s !== scope) : [...p.scopes, scope],
    }))
  }

  async function save() {
    if (!form.name) { setError('Name is required'); return }
    if (form.scopes.length === 0) { setError('Select at least one scope'); return }
    setError('')
    try {
      const result = await create.mutateAsync(form)
      const newKey = (result as { data?: ApiKey } | undefined)?.data
      if (newKey?.keyValue) setRevealed(newKey.keyValue)
      setShowModal(false)
      setForm(blankForm())
    } catch (e) { setError(e instanceof Error ? e.message : 'Create failed') }
  }

  return (
    <div>
      <PageHeader
        title="API Keys"
        subtitle="Machine-to-machine credentials for BaaS card platform access"
        actions={
          <button onClick={() => { setForm(blankForm()); setError(''); setShowModal(true) }}
            className="px-4 py-2 rounded-lg text-sm font-medium text-white"
            style={{ background: 'var(--color-primary)' }}>+ Issue Key</button>
        }
      />

      {revealed && <RevealedKey value={revealed} />}

      {isLoading ? (
        <div className="mt-4 p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
      ) : (
        <div className="mt-4 rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                {['Name', 'Scopes', 'Status', 'Last Used', 'Created', ''].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                    style={{ color: 'var(--color-muted)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {keys.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>No API keys issued.</td></tr>
              )}
              {keys.map(k => (
                <tr key={k.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                  <td className="px-4 py-3 text-sm font-medium" style={{ color: 'var(--color-text)' }}>{k.name}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {k.scopes.map(s => (
                        <span key={s} className="text-xs px-1.5 py-0.5 rounded font-mono"
                          style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
                          {s}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge label={k.active ? 'ACTIVE' : 'REVOKED'} variant={k.active ? 'success' : 'neutral'} />
                  </td>
                  <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>
                    {k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleString() : 'Never'}
                  </td>
                  <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>
                    {new Date(k.createdAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3">
                    {k.active && <RevokeRow apiKey={k} />}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={showModal} onClose={() => setShowModal(false)} title="Issue API Key" size="md"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowModal(false)} className="px-4 py-2 text-sm rounded-lg"
              style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={save} disabled={create.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}>
              {create.isPending ? 'Issuing…' : 'Issue Key'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-4">
          {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Key Name</label>
            <input value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))}
              placeholder="e.g. Mobile App Production"
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
          </div>
          <div>
            <label className="block text-xs font-medium mb-2" style={{ color: 'var(--color-muted)' }}>Scopes</label>
            <div className="grid grid-cols-2 gap-2">
              {ALL_SCOPES.map(scope => (
                <label key={scope} className="flex items-center gap-2 text-xs cursor-pointer" style={{ color: 'var(--color-text)' }}>
                  <input type="checkbox" checked={form.scopes.includes(scope)}
                    onChange={() => toggleScope(scope)} className="w-3.5 h-3.5" />
                  <span className="font-mono">{scope}</span>
                </label>
              ))}
            </div>
          </div>
        </div>
      </Modal>
    </div>
  )
}
