// web-react/src/app/features/system/GlobalConfigPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { useGlobalConfigurations, useUpdateGlobalConfig } from './api/useSystem'
import type { GlobalConfiguration } from './api/types'

// ── Inline edit row — hook lives at component level per row ───────────────────

function ConfigRow({ config }: { config: GlobalConfiguration }) {
  const update = useUpdateGlobalConfig(config.id)
  const [editing, setEditing] = useState(false)
  const [value,   setValue]   = useState<string>(
    config.stringValue ?? String(config.numericValue ?? config.booleanValue ?? config.value ?? '')
  )
  const [enabled, setEnabled] = useState(config.enabled)

  async function save() {
    const body: Parameters<typeof update.mutateAsync>[0] = { enabled }
    if (config.numericValue !== undefined || config.value !== undefined) {
      body.value = Number(value)
    } else if (config.stringValue !== undefined) {
      body.stringValue = value
    }
    await update.mutateAsync(body)
    setEditing(false)
  }

  function detectType(): 'string' | 'number' | 'boolean' {
    if (config.booleanValue !== undefined) return 'boolean'
    if (config.numericValue !== undefined || config.value !== undefined) return 'number'
    return 'string'
  }

  const type = detectType()

  return (
    <tr style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
      <td className="px-4 py-3 text-xs font-mono font-medium" style={{ color: 'var(--color-text)' }}>
        {config.name}
      </td>
      <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)', maxWidth: 240 }}>
        {config.description ?? '—'}
      </td>

      {/* Value cell */}
      <td className="px-4 py-3">
        {editing ? (
          type === 'boolean' ? (
            <select
              value={String(enabled)}
              onChange={e => setEnabled(e.target.value === 'true')}
              className="px-2 py-1 rounded text-xs outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
            >
              <option value="true">true</option>
              <option value="false">false</option>
            </select>
          ) : (
            <input
              type={type === 'number' ? 'number' : 'text'}
              value={value}
              onChange={e => setValue(e.target.value)}
              autoFocus
              className="px-2 py-1 rounded text-xs outline-none w-36"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
            />
          )
        ) : (
          <span className="text-xs tabular-nums font-mono" style={{ color: 'var(--color-text)' }}>
            {type === 'boolean'
              ? String(config.booleanValue)
              : (config.stringValue ?? config.numericValue ?? config.value ?? '—')}
          </span>
        )}
      </td>

      {/* Enabled toggle */}
      <td className="px-4 py-3">
        <button
          onClick={async () => {
            const next = !enabled
            setEnabled(next)
            await update.mutateAsync({ enabled: next })
          }}
          disabled={update.isPending}
          className="text-xs px-2 py-1 rounded disabled:opacity-60"
          style={{
            background: enabled ? 'var(--color-success)' : 'var(--bg-subtle)',
            color:      enabled ? '#fff' : 'var(--color-muted)',
            border:     '1px solid var(--color-border)',
          }}
        >
          {enabled ? 'Enabled' : 'Disabled'}
        </button>
      </td>

      {/* Actions */}
      <td className="px-4 py-3">
        {editing ? (
          <div className="flex gap-1.5">
            <button
              onClick={save}
              disabled={update.isPending}
              className="text-xs px-2 py-1 rounded disabled:opacity-60 text-white"
              style={{ background: 'var(--color-primary)' }}
            >
              {update.isPending ? '…' : 'Save'}
            </button>
            <button onClick={() => setEditing(false)} className="text-xs px-2 py-1 rounded"
              style={{ color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
              Cancel
            </button>
          </div>
        ) : (
          <button onClick={() => setEditing(true)} className="text-xs px-2 py-1 rounded"
            style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>
            Edit
          </button>
        )}
      </td>
    </tr>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function GlobalConfigPage() {
  const { data, isLoading } = useGlobalConfigurations()
  const configs: GlobalConfiguration[] = (data as { data?: GlobalConfiguration[] } | undefined)?.data ?? []

  const [search, setSearch] = useState('')

  const filtered = configs.filter(c =>
    !search || c.name.toLowerCase().includes(search.toLowerCase())
  )

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Global Configuration"
        subtitle="Platform-wide feature flags and settings"
      />

      <div className="mb-4">
        <input
          type="text"
          placeholder="Search configuration keys…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="px-3 py-2 rounded-lg text-sm outline-none"
          style={{
            background: 'var(--bg-subtle)',
            border: '1px solid var(--color-border)',
            color: 'var(--color-text)',
            width: 300,
          }}
        />
      </div>

      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
              {['Key', 'Description', 'Value', 'Status', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                  style={{ color: 'var(--color-muted)' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 && (
              <tr><td colSpan={5} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                No configurations found.
              </td></tr>
            )}
            {filtered.map(c => <ConfigRow key={c.id} config={c} />)}
          </tbody>
        </table>
      </div>
    </div>
  )
}
