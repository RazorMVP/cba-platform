// web-react/src/app/features/cards/WebhooksPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useWebhooks, useCreateWebhook, useDeleteWebhook, useWebhookDeliveries } from './api/useCards'
import type { Webhook, WebhookRequest } from './api/types'

const EVENT_CATEGORIES: Record<string, string[]> = {
  Authorization: ['AUTHORIZATION.APPROVED', 'AUTHORIZATION.DECLINED', 'AUTHORIZATION.REVERSED'],
  'Card Lifecycle': ['CARD.ISSUED', 'CARD.ACTIVATED', 'CARD.BLOCKED', 'CARD.UNBLOCKED', 'CARD.EXPIRED', 'CARD.PIN_CHANGED', 'CARD.LIMIT_CHANGED'],
  Fraud: ['FRAUD.RULE_TRIGGERED', 'FRAUD.CARD_STEP_UP', 'FRAUD.CARD_DECLINED_HIGH_RISK'],
  Dispute: ['DISPUTE.RAISED', 'DISPUTE.RESOLVED'],
}
function blankForm(): WebhookRequest { return { name: '', callbackUrl: '', events: [], secret: '' } }

function DeliveryPanel({ webhookId }: { webhookId: string }) {
  const { data, isLoading } = useWebhookDeliveries(webhookId)
  const deliveries = (data as { data?: import('./api/types').WebhookDelivery[] } | undefined)?.data ?? []

  if (isLoading) return <p className="text-xs p-3" style={{ color: 'var(--color-muted)' }}>Loading deliveries…</p>
  if (deliveries.length === 0) return <p className="text-xs p-3" style={{ color: 'var(--color-muted)' }}>No deliveries yet.</p>

  return (
    <div className="divide-y" style={{ borderColor: 'var(--color-border)' }}>
      {deliveries.map(d => (
        <div key={d.id} className="px-4 py-2 flex items-center justify-between gap-4">
          <div className="min-w-0">
            <p className="text-xs font-mono" style={{ color: 'var(--color-text)' }}>{d.eventType}</p>
            <p className="text-xs mt-0.5" style={{ color: 'var(--color-muted)' }}>
              {d.lastAttemptAt ? new Date(d.lastAttemptAt).toLocaleString() : '—'} · {d.attemptCount} attempt{d.attemptCount !== 1 ? 's' : ''}
            </p>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {d.httpStatus && (
              <span className="font-mono text-xs" style={{ color: d.httpStatus < 300 ? 'var(--color-success)' : 'var(--color-error)' }}>
                {d.httpStatus}
              </span>
            )}
            <StatusBadge
              label={d.status}
              variant={d.status === 'DELIVERED' ? 'success' : d.status === 'FAILED' ? 'error' : 'warning'}
            />
          </div>
        </div>
      ))}
    </div>
  )
}

function DeleteRow({ webhook }: { webhook: Webhook }) {
  const del = useDeleteWebhook(webhook.id)
  const [confirming, setConfirming] = useState(false)
  return confirming ? (
    <span className="flex gap-1">
      <button onClick={() => del.mutate()} disabled={del.isPending}
        className="text-xs px-2 py-1 rounded disabled:opacity-50"
        style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)', border: '1px solid var(--color-error)' }}>
        {del.isPending ? '…' : 'Delete'}
      </button>
      <button onClick={() => setConfirming(false)} className="text-xs px-2 py-1 rounded"
        style={{ color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>Cancel</button>
    </span>
  ) : (
    <button onClick={() => setConfirming(true)} className="text-xs px-2 py-1 rounded"
      style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}>Delete</button>
  )
}

export default function WebhooksPage() {
  const { data, isLoading } = useWebhooks()
  const webhooks = (data as { data?: Webhook[] } | undefined)?.data ?? []

  const create = useCreateWebhook()
  const [showModal, setShowModal]   = useState(false)
  const [form, setForm]             = useState<WebhookRequest>(blankForm())
  const [error, setError]           = useState('')
  const [selectedId, setSelectedId] = useState<string | null>(null)

  function toggleEvent(ev: string) {
    setForm(p => ({
      ...p,
      events: p.events.includes(ev) ? p.events.filter(e => e !== ev) : [...p.events, ev],
    }))
  }

  function toggleCategory(evts: string[]) {
    const allSelected = evts.every(e => form.events.includes(e))
    setForm(p => ({
      ...p,
      events: allSelected
        ? p.events.filter(e => !evts.includes(e))
        : [...new Set([...p.events, ...evts])],
    }))
  }

  async function save() {
    if (!form.name || !form.callbackUrl || !form.secret) { setError('Name, URL, and secret are required'); return }
    if (form.events.length === 0) { setError('Select at least one event'); return }
    setError('')
    try { await create.mutateAsync(form); setShowModal(false); setForm(blankForm()) }
    catch (e) { setError(e instanceof Error ? e.message : 'Create failed') }
  }

  const selected = webhooks.find(w => w.id === selectedId) ?? null

  return (
    <div>
      <PageHeader
        title="Webhooks"
        subtitle="Real-time event delivery to external endpoints via HMAC-signed HTTP POST"
        actions={
          <button onClick={() => { setForm(blankForm()); setError(''); setShowModal(true) }}
            className="px-4 py-2 rounded-lg text-sm font-medium text-white"
            style={{ background: 'var(--color-primary)' }}>+ Register Webhook</button>
        }
      />

      <div className="flex gap-4">
        {/* Webhook list */}
        <div className="flex-1 space-y-3">
          {isLoading && <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>}
          {!isLoading && webhooks.length === 0 && (
            <div className="p-8 text-sm text-center rounded-xl" style={{ color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
              No webhooks registered.
            </div>
          )}
          {webhooks.map(w => (
            <div key={w.id}
              onClick={() => setSelectedId(id => id === w.id ? null : w.id)}
              className="rounded-xl p-4 cursor-pointer"
              style={{
                background: selectedId === w.id ? 'var(--bg-subtle)' : 'var(--bg-card)',
                border: `1px solid ${selectedId === w.id ? 'var(--color-primary)' : 'var(--color-border)'}`,
              }}>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <p className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>{w.name}</p>
                    <StatusBadge label={w.active ? 'ACTIVE' : 'INACTIVE'} variant={w.active ? 'success' : 'neutral'} />
                  </div>
                  <p className="text-xs font-mono truncate" style={{ color: 'var(--color-muted)' }}>{w.callbackUrl}</p>
                  <div className="flex flex-wrap gap-1 mt-2">
                    {w.events.slice(0, 4).map(e => (
                      <span key={e} className="text-xs px-1.5 py-0.5 rounded"
                        style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
                        {e}
                      </span>
                    ))}
                    {w.events.length > 4 && (
                      <span className="text-xs px-1.5 py-0.5 rounded" style={{ color: 'var(--color-muted)' }}>
                        +{w.events.length - 4} more
                      </span>
                    )}
                  </div>
                </div>
                <DeleteRow webhook={w} />
              </div>
            </div>
          ))}
        </div>

        {/* Delivery log panel */}
        {selected && (
          <div className="w-96 shrink-0 rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
            <div className="px-4 py-3 flex items-center justify-between"
              style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
              <p className="text-xs font-semibold" style={{ color: 'var(--color-text)' }}>
                Delivery Log — {selected.name}
              </p>
              <button onClick={() => setSelectedId(null)} className="text-xs" style={{ color: 'var(--color-muted)' }}>✕</button>
            </div>
            <DeliveryPanel webhookId={selected.id} />
          </div>
        )}
      </div>

      {/* Create modal */}
      <Modal open={showModal} onClose={() => setShowModal(false)} title="Register Webhook" size="md"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowModal(false)} className="px-4 py-2 text-sm rounded-lg"
              style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={save} disabled={create.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}>
              {create.isPending ? 'Registering…' : 'Register'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-4">
          {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
          <WF label="Name" value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} />
          <WF label="Callback URL" value={form.callbackUrl} onChange={e => setForm(p => ({ ...p, callbackUrl: e.target.value }))} type="url" placeholder="https://your-app.com/webhooks/card" />
          <WF label="Signing Secret" value={form.secret} onChange={e => setForm(p => ({ ...p, secret: e.target.value }))} type="password" placeholder="min 16 chars recommended" />
          <div>
            <label className="block text-xs font-medium mb-2" style={{ color: 'var(--color-muted)' }}>Events</label>
            <div className="space-y-3">
              {Object.entries(EVENT_CATEGORIES).map(([cat, evts]) => (
                <div key={cat}>
                  <button onClick={() => toggleCategory(evts)}
                    className="text-xs font-semibold mb-1 flex items-center gap-1"
                    style={{ color: 'var(--color-text)' }}>
                    <span>{evts.every(e => form.events.includes(e)) ? '☑' : '☐'}</span> {cat}
                  </button>
                  <div className="pl-4 space-y-1">
                    {evts.map(ev => (
                      <label key={ev} className="flex items-center gap-2 text-xs cursor-pointer" style={{ color: 'var(--color-muted)' }}>
                        <input type="checkbox" checked={form.events.includes(ev)} onChange={() => toggleEvent(ev)} className="w-3.5 h-3.5" />
                        <span className="font-mono">{ev}</span>
                      </label>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </Modal>
    </div>
  )
}

function WF({ label, ...props }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <input {...props} className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
    </div>
  )
}
