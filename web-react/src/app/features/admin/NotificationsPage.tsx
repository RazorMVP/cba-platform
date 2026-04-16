// web-react/src/app/features/admin/NotificationsPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import {
  useNotificationTemplates, useCreateNotificationTemplate,
  useUpdateNotificationTemplate, useDeactivateNotificationTemplate,
  useSendTestNotification, useNotificationHistory,
} from './api/useAdmin'
import type {
  NotificationTemplate, CreateTemplateRequest,
  NotificationDeliveryMethod,
} from './api/types'

const EVENT_TYPES = [
  'ACCOUNT_OPENED', 'ACCOUNT_CLOSED', 'LARGE_TRANSACTION',
  'LOAN_APPROVED', 'LOAN_DISBURSED', 'LOAN_DUE', 'LOAN_ARREARS',
  'CUSTOMER_CREATED', 'KYC_APPROVED', 'FAILED_LOGIN', 'PROFILE_CHANGED',
]

// ── Deactivate (hook at top level) ────────────────────────────────────────────

function DeactivateRow({ tmpl, onDone }: { tmpl: NotificationTemplate; onDone: () => void }) {
  const deactivate = useDeactivateNotificationTemplate(tmpl.id)
  return (
    <div className="flex justify-end gap-3 mt-4">
      <button onClick={onDone} className="text-xs px-3 py-1.5 rounded-lg"
        style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
      <button
        onClick={async () => { await deactivate.mutateAsync(); onDone() }}
        disabled={deactivate.isPending}
        className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
        style={{ background: 'var(--color-error)' }}
      >
        {deactivate.isPending ? 'Deactivating…' : 'Deactivate'}
      </button>
    </div>
  )
}

// ── Test send modal ───────────────────────────────────────────────────────────

function TestSendModal({ tmpl, onClose }: { tmpl: NotificationTemplate; onClose: () => void }) {
  const send = useSendTestNotification()
  const [recipient, setRecipient] = useState('')
  const [sent, setSent] = useState(false)

  async function submit() {
    await send.mutateAsync({ templateId: tmpl.id, recipientRef: recipient })
    setSent(true)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.5)' }}>
      <div className="w-full max-w-md rounded-2xl p-6" style={{ background: 'var(--bg-card)' }}>
        <h2 className="text-base font-semibold mb-1" style={{ color: 'var(--color-text)' }}>
          Test Send — {tmpl.name}
        </h2>
        <p className="text-xs mb-4" style={{ color: 'var(--color-muted)' }}>
          {tmpl.deliveryMethod === 'EMAIL' ? 'Enter an email address' : 'Enter a phone number'}
        </p>

        {sent ? (
          <div>
            <p className="text-sm" style={{ color: 'var(--color-success)' }}>
              Test notification dispatched successfully.
            </p>
            <div className="flex justify-end mt-4">
              <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
                style={{ background: 'var(--color-primary)', color: '#fff' }}>Close</button>
            </div>
          </div>
        ) : (
          <div>
            <input
              type={tmpl.deliveryMethod === 'EMAIL' ? 'email' : 'tel'}
              value={recipient}
              onChange={e => setRecipient(e.target.value)}
              placeholder={tmpl.deliveryMethod === 'EMAIL' ? 'test@example.com' : '+1234567890'}
              className="w-full px-2 py-1.5 rounded text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
            />
            {send.isError && (
              <p className="text-xs mt-2" style={{ color: 'var(--color-error)' }}>Failed to send test notification.</p>
            )}
            <div className="flex justify-end gap-3 mt-4">
              <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
                style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
              <button onClick={submit} disabled={!recipient || send.isPending}
                className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
                style={{ background: 'var(--color-primary)' }}>
                {send.isPending ? 'Sending…' : 'Send Test'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

// ── Create / Edit modal ───────────────────────────────────────────────────────

function TemplateModal({ initial, onClose }: { initial?: NotificationTemplate; onClose: () => void }) {
  const isEdit = !!initial
  const create = useCreateNotificationTemplate()
  const update = useUpdateNotificationTemplate(initial?.id ?? '')

  const [form, setForm] = useState<CreateTemplateRequest>({
    name:           initial?.name           ?? '',
    eventType:      initial?.eventType      ?? EVENT_TYPES[0],
    deliveryMethod: initial?.deliveryMethod ?? 'EMAIL',
    subject:        initial?.subject        ?? '',
    body:           initial?.body           ?? '',
  })

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
          {isEdit ? 'Edit Template' : 'New Template'}
        </h2>

        <div className="space-y-3">
          <div>
            <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>Name</label>
            <input type="text" value={form.name}
              onChange={e => setForm(prev => ({ ...prev, name: e.target.value }))}
              className="w-full px-2 py-1.5 rounded text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>Event Type</label>
              <select value={form.eventType}
                onChange={e => setForm(prev => ({ ...prev, eventType: e.target.value }))}
                className="w-full px-2 py-1.5 rounded text-sm outline-none"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
              >
                {EVENT_TYPES.map(ev => <option key={ev} value={ev}>{ev}</option>)}
              </select>
            </div>

            <div>
              <label className="block text-xs mb-2 font-medium" style={{ color: 'var(--color-muted)' }}>Delivery Method</label>
              <div className="flex gap-2">
                {(['EMAIL', 'SMS'] as NotificationDeliveryMethod[]).map(m => (
                  <button key={m} onClick={() => setForm(prev => ({ ...prev, deliveryMethod: m }))}
                    className="text-xs px-3 py-1.5 rounded-full"
                    style={{
                      background: form.deliveryMethod === m ? 'var(--color-primary)' : 'var(--bg-subtle)',
                      color:      form.deliveryMethod === m ? '#fff' : 'var(--color-muted)',
                      border:     '1px solid var(--color-border)',
                    }}>
                    {m}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {form.deliveryMethod === 'EMAIL' && (
            <div>
              <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>Subject</label>
              <input type="text" value={form.subject ?? ''}
                onChange={e => setForm(prev => ({ ...prev, subject: e.target.value }))}
                className="w-full px-2 py-1.5 rounded text-sm outline-none"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
              />
            </div>
          )}

          <div>
            <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>
              Body
              <span className="ml-1 font-normal" style={{ color: 'var(--color-muted)' }}>
                — use {'{{customerName}}'}, {'{{amount}}'}, {'{{loanId}}'} as placeholders
              </span>
            </label>
            <textarea rows={5} value={form.body}
              onChange={e => setForm(prev => ({ ...prev, body: e.target.value }))}
              className="w-full px-2 py-1.5 rounded text-sm outline-none resize-y font-mono"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
            />
          </div>
        </div>

        {isError && (
          <p className="text-xs mt-3" style={{ color: 'var(--color-error)' }}>Failed to save template.</p>
        )}

        <div className="flex justify-end gap-3 mt-5">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button onClick={save} disabled={isPending}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {isPending ? 'Saving…' : isEdit ? 'Update Template' : 'Create Template'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── History tab ───────────────────────────────────────────────────────────────

function HistoryTab() {
  const { data, isLoading } = useNotificationHistory()
  const logs = (data as { data?: ReturnType<typeof useNotificationHistory>['data'] } | undefined)
  const history = (logs?.data as { data?: unknown[] } | undefined)?.data ?? []

  if (isLoading) return <p className="text-xs py-4" style={{ color: 'var(--color-muted)' }}>Loading history…</p>

  return (
    <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
            {['Event', 'Method', 'Recipient', 'Status', 'Sent At'].map(h => (
              <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                style={{ color: 'var(--color-muted)' }}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {history.length === 0 && (
            <tr><td colSpan={5} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
              No delivery history.
            </td></tr>
          )}
          {(history as Array<{
            id: string; eventType: string; deliveryMethod: string;
            recipientRef?: string; status: string; sentAt: string
          }>).map(log => (
            <tr key={log.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
              <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>{log.eventType}</td>
              <td className="px-4 py-3">
                <StatusBadge label={log.deliveryMethod} variant={log.deliveryMethod === 'EMAIL' ? 'info' : 'warning'} />
              </td>
              <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>{log.recipientRef ?? '—'}</td>
              <td className="px-4 py-3">
                <StatusBadge
                  label={log.status}
                  variant={log.status === 'SENT' ? 'success' : log.status === 'FAILED' ? 'error' : 'neutral'}
                />
              </td>
              <td className="px-4 py-3 text-xs tabular-nums" style={{ color: 'var(--color-muted)' }}>
                {new Date(log.sentAt).toLocaleString()}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function NotificationsPage() {
  const { data, isLoading } = useNotificationTemplates()
  const templates: NotificationTemplate[] =
    (data as { data?: NotificationTemplate[] } | undefined)?.data ?? []

  const [tab,        setTab]        = useState<'templates' | 'history'>('templates')
  const [creating,   setCreating]   = useState(false)
  const [editing,    setEditing]    = useState<NotificationTemplate | null>(null)
  const [testing,    setTesting]    = useState<NotificationTemplate | null>(null)
  const [deactivating, setDeactivating] = useState<NotificationTemplate | null>(null)

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Notifications"
        subtitle="Email and SMS notification templates"
        actions={
          tab === 'templates' ? (
            <button onClick={() => setCreating(true)}
              className="text-xs px-3 py-1.5 rounded-lg text-white"
              style={{ background: 'var(--color-primary)' }}>
              + New Template
            </button>
          ) : undefined
        }
      />

      {/* Tabs */}
      <div className="flex gap-1 mb-5 p-1 rounded-xl w-fit"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)' }}>
        {(['templates', 'history'] as const).map(t => (
          <button key={t} onClick={() => setTab(t)}
            className="text-xs px-4 py-1.5 rounded-lg capitalize transition-colors"
            style={{
              background: tab === t ? 'var(--bg-card)' : 'transparent',
              color:      tab === t ? 'var(--color-text)' : 'var(--color-muted)',
              fontWeight: tab === t ? 600 : 400,
              border:     tab === t ? '1px solid var(--color-border)' : '1px solid transparent',
            }}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'history' ? <HistoryTab /> : (
        <>
          <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                  {['Name', 'Event', 'Method', 'Subject', 'Status', ''].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                      style={{ color: 'var(--color-muted)' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {templates.length === 0 && (
                  <tr><td colSpan={6} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                    No templates configured.
                  </td></tr>
                )}
                {templates.map(t => (
                  <tr key={t.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                    <td className="px-4 py-3 font-medium text-sm" style={{ color: 'var(--color-text)' }}>{t.name}</td>
                    <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>{t.eventType}</td>
                    <td className="px-4 py-3">
                      <StatusBadge label={t.deliveryMethod} variant={t.deliveryMethod === 'EMAIL' ? 'info' : 'warning'} />
                    </td>
                    <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>
                      {t.subject ?? '—'}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge label={t.active ? 'ACTIVE' : 'INACTIVE'} variant={t.active ? 'success' : 'neutral'} />
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex gap-1.5">
                        <button onClick={() => setTesting(t)}
                          className="text-xs px-2 py-1 rounded"
                          style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>
                          Test
                        </button>
                        <button onClick={() => setEditing(t)}
                          className="text-xs px-2 py-1 rounded"
                          style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>
                          Edit
                        </button>
                        {t.active && (
                          <button onClick={() => setDeactivating(t)}
                            className="text-xs px-2 py-1 rounded"
                            style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}>
                            Deactivate
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Deactivate confirm */}
          {deactivating && (
            <div className="mt-4 rounded-xl p-5" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
              <p className="text-sm font-semibold" style={{ color: 'var(--color-text)' }}>
                Deactivate <span className="font-mono">{deactivating.name}</span>?
              </p>
              <p className="text-xs mt-1" style={{ color: 'var(--color-muted)' }}>
                This template will no longer fire on events.
              </p>
              <DeactivateRow tmpl={deactivating} onDone={() => setDeactivating(null)} />
            </div>
          )}
        </>
      )}

      {creating   && <TemplateModal onClose={() => setCreating(false)} />}
      {editing    && <TemplateModal initial={editing} onClose={() => setEditing(null)} />}
      {testing    && <TestSendModal tmpl={testing} onClose={() => setTesting(null)} />}
    </div>
  )
}
