// web-react/src/app/features/admin/HooksPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { useHooks, useCreateHook, useUpdateHook, useDeleteHook } from './api/useAdmin'
import type { Hook, CreateHookRequest, HookType } from './api/types'

const AVAILABLE_EVENTS = [
  'LOAN_APPROVED', 'LOAN_DISBURSED', 'LOAN_REPAYMENT', 'LOAN_ARREARS',
  'ACCOUNT_OPENED', 'ACCOUNT_CLOSED', 'LARGE_TRANSACTION',
  'CUSTOMER_CREATED', 'KYC_APPROVED', 'FAILED_LOGIN',
]

// ── Delete confirm (hook at top level) ────────────────────────────────────────

function DeleteHookRow({ hook, onDone }: { hook: Hook; onDone: () => void }) {
  const del = useDeleteHook(hook.id)
  return (
    <div className="flex justify-end gap-3 mt-4">
      <button onClick={onDone} className="text-xs px-3 py-1.5 rounded-lg"
        style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
      <button
        onClick={async () => { await del.mutateAsync(); onDone() }}
        disabled={del.isPending}
        className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
        style={{ background: 'var(--color-error)' }}
      >
        {del.isPending ? 'Deleting…' : 'Delete Hook'}
      </button>
    </div>
  )
}

// ── Create / Edit modal ───────────────────────────────────────────────────────

function HookModal({ initial, onClose }: { initial?: Hook; onClose: () => void }) {
  const isEdit = !!initial
  const create = useCreateHook()
  const update = useUpdateHook(initial?.id ?? '')

  const [form, setForm] = useState<CreateHookRequest>({
    name:     initial?.name     ?? '',
    hookType: initial?.hookType ?? 'WEB',
    url:      initial?.url      ?? '',
    events:   initial?.events   ?? [],
  })

  function toggleEvent(ev: string) {
    setForm(prev => ({
      ...prev,
      events: prev.events.includes(ev)
        ? prev.events.filter(e => e !== ev)
        : [...prev.events, ev],
    }))
  }

  async function save() {
    if (isEdit) await update.mutateAsync(form)
    else        await create.mutateAsync(form)
    onClose()
  }

  const isPending = create.isPending || update.isPending
  const isError   = create.isError   || update.isError

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.5)' }}>
      <div className="w-full max-w-lg rounded-2xl p-6 overflow-y-auto" style={{ background: 'var(--bg-card)', maxHeight: '90vh' }}>
        <h2 className="text-base font-semibold mb-4" style={{ color: 'var(--color-text)' }}>
          {isEdit ? 'Edit Hook' : 'New Hook'}
        </h2>

        <div className="space-y-3">
          {/* Type toggle */}
          <div>
            <label className="block text-xs mb-2 font-medium" style={{ color: 'var(--color-muted)' }}>Type</label>
            <div className="flex gap-2">
              {(['WEB', 'SMS'] as HookType[]).map(t => (
                <button key={t} onClick={() => setForm(prev => ({ ...prev, hookType: t }))}
                  className="text-xs px-3 py-1.5 rounded-full"
                  style={{
                    background: form.hookType === t ? 'var(--color-primary)' : 'var(--bg-subtle)',
                    color:      form.hookType === t ? '#fff' : 'var(--color-muted)',
                    border:     '1px solid var(--color-border)',
                  }}>
                  {t}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>Name</label>
            <input type="text" value={form.name}
              onChange={e => setForm(prev => ({ ...prev, name: e.target.value }))}
              className="w-full px-2 py-1.5 rounded text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
            />
          </div>

          <div>
            <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>
              {form.hookType === 'WEB' ? 'Callback URL' : 'SMS Gateway URL'}
            </label>
            <input type="text" value={form.url}
              onChange={e => setForm(prev => ({ ...prev, url: e.target.value }))}
              placeholder={form.hookType === 'WEB' ? 'https://…' : 'sms://…'}
              className="w-full px-2 py-1.5 rounded text-sm outline-none font-mono"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
            />
          </div>

          {/* Events */}
          <div>
            <label className="block text-xs mb-2 font-medium" style={{ color: 'var(--color-muted)' }}>
              Events ({form.events.length} selected)
            </label>
            <div className="flex flex-wrap gap-2">
              {AVAILABLE_EVENTS.map(ev => (
                <button key={ev} onClick={() => toggleEvent(ev)}
                  className="text-xs px-2.5 py-1 rounded-full"
                  style={{
                    background: form.events.includes(ev) ? 'var(--color-primary)' : 'var(--bg-subtle)',
                    color:      form.events.includes(ev) ? '#fff' : 'var(--color-muted)',
                    border:     '1px solid var(--color-border)',
                  }}>
                  {ev.toLowerCase().replace(/_/g, ' ')}
                </button>
              ))}
            </div>
          </div>
        </div>

        {isError && (
          <p className="text-xs mt-3" style={{ color: 'var(--color-error)' }}>Failed to save hook.</p>
        )}

        <div className="flex justify-end gap-3 mt-5">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button onClick={save} disabled={isPending}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {isPending ? 'Saving…' : isEdit ? 'Update Hook' : 'Create Hook'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function HooksPage() {
  const { data, isLoading } = useHooks()
  const hooks: Hook[] = (data as { data?: Hook[] } | undefined)?.data ?? []

  const [creating, setCreating] = useState(false)
  const [editing,  setEditing]  = useState<Hook | null>(null)
  const [deleting, setDeleting] = useState<Hook | null>(null)

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Hooks"
        subtitle="Web and SMS event webhooks"
        actions={
          <button onClick={() => setCreating(true)}
            className="text-xs px-3 py-1.5 rounded-lg text-white"
            style={{ background: 'var(--color-primary)' }}>
            + New Hook
          </button>
        }
      />

      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
              {['Name', 'Type', 'URL', 'Events', 'Status', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                  style={{ color: 'var(--color-muted)' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {hooks.length === 0 && (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                No hooks configured.
              </td></tr>
            )}
            {hooks.map(h => (
              <tr key={h.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                <td className="px-4 py-3 font-medium text-sm" style={{ color: 'var(--color-text)' }}>{h.name}</td>
                <td className="px-4 py-3">
                  <StatusBadge label={h.hookType} variant={h.hookType === 'WEB' ? 'info' : 'warning'} />
                </td>
                <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)', maxWidth: 220 }}>
                  <span className="truncate block">{h.url}</span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1">
                    {h.events.slice(0, 3).map(ev => (
                      <span key={ev} className="text-xs px-2 py-0.5 rounded-full"
                        style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
                        {ev.toLowerCase().replace(/_/g, ' ')}
                      </span>
                    ))}
                    {h.events.length > 3 && (
                      <span className="text-xs px-2 py-0.5 rounded-full"
                        style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
                        +{h.events.length - 3}
                      </span>
                    )}
                  </div>
                </td>
                <td className="px-4 py-3">
                  <StatusBadge label={h.enabled ? 'ENABLED' : 'DISABLED'} variant={h.enabled ? 'success' : 'neutral'} />
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-1.5">
                    <button onClick={() => setEditing(h)}
                      className="text-xs px-2 py-1 rounded"
                      style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>Edit</button>
                    <button onClick={() => setDeleting(h)}
                      className="text-xs px-2 py-1 rounded"
                      style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}>Delete</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Delete confirm */}
      {deleting && (
        <div className="mt-4 rounded-xl p-5" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <p className="text-sm font-semibold" style={{ color: 'var(--color-text)' }}>
            Delete <span className="font-mono">{deleting.name}</span>?
          </p>
          <p className="text-xs mt-1" style={{ color: 'var(--color-muted)' }}>This action cannot be undone.</p>
          <DeleteHookRow hook={deleting} onDone={() => setDeleting(null)} />
        </div>
      )}

      {creating && <HookModal onClose={() => setCreating(false)} />}
      {editing  && <HookModal initial={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
