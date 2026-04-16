// web-react/src/app/features/reports/ReportMailingPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import {
  useMailingJobs, useCreateMailingJob, useUpdateMailingJob,
  useDeleteMailingJob, useRunMailingJob,
} from './api/useReports'
import type { ReportMailingJob, MailingJobRequest, OutputType } from './api/types'

const OUTPUT_TYPES: OutputType[] = ['CSV', 'PDF', 'XLS']

// Common RRULE presets for report scheduling
const RRULE_PRESETS = [
  { label: 'Daily',        value: 'FREQ=DAILY' },
  { label: 'Weekly (Mon)', value: 'FREQ=WEEKLY;BYDAY=MO' },
  { label: 'Monthly (1st)',value: 'FREQ=MONTHLY;BYMONTHDAY=1' },
  { label: 'Custom',       value: '' },
]

function outputVariant(t: OutputType): 'success' | 'info' | 'warning' {
  if (t === 'CSV') return 'success'
  if (t === 'PDF') return 'error' as 'warning'
  return 'info'
}

function formatTs(ts: string | undefined): string {
  if (!ts) return '—'
  return new Date(ts).toLocaleString()
}

// ── Delete action (hook at top level) ─────────────────────────────────────────

function DeleteMailingRow({ id, onDone }: { id: string; onDone: () => void }) {
  const del = useDeleteMailingJob(id)
  return (
    <div className="flex justify-end gap-3 mt-4">
      <button onClick={onDone} className="text-xs px-3 py-1.5 rounded-lg"
        style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
        Cancel
      </button>
      <button
        onClick={async () => { await del.mutateAsync(); onDone() }}
        disabled={del.isPending}
        className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
        style={{ background: 'var(--color-error)' }}
      >
        {del.isPending ? 'Deleting…' : 'Delete Job'}
      </button>
    </div>
  )
}

// ── Run-now button (hook at top level) ────────────────────────────────────────

function RunNowButton({ id }: { id: string }) {
  const run = useRunMailingJob(id)
  return (
    <button
      onClick={() => run.mutate()}
      disabled={run.isPending}
      className="text-xs px-2 py-1 rounded"
      style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}
    >
      {run.isPending ? 'Sending…' : 'Send Now'}
    </button>
  )
}

// ── Create / Edit modal ───────────────────────────────────────────────────────

function MailingJobModal({
  initial,
  onClose,
}: {
  initial?: ReportMailingJob
  onClose: () => void
}) {
  const isEdit = !!initial
  const create = useCreateMailingJob()
  const update = useUpdateMailingJob(initial?.id ?? '')

  const [form, setForm] = useState<MailingJobRequest>({
    name:            initial?.name ?? '',
    reportName:      initial?.reportName ?? '',
    emailRecipients: initial?.emailRecipients ?? '',
    emailSubject:    initial?.emailSubject ?? '',
    emailMessage:    initial?.emailMessage ?? '',
    recurrence:      initial?.recurrence ?? 'FREQ=DAILY',
    outputType:      initial?.outputType ?? 'CSV',
    params:          initial?.params ?? {},
  })

  const [presetIdx, setPresetIdx] = useState(() => {
    const idx = RRULE_PRESETS.findIndex(p => p.value === (initial?.recurrence ?? 'FREQ=DAILY'))
    return idx >= 0 ? idx : RRULE_PRESETS.length - 1 // fallback to Custom
  })

  const [customRrule, setCustomRrule] = useState(
    RRULE_PRESETS.some(p => p.value === (initial?.recurrence ?? 'FREQ=DAILY'))
      ? ''
      : (initial?.recurrence ?? '')
  )

  function set<K extends keyof MailingJobRequest>(k: K, v: MailingJobRequest[K]) {
    setForm(prev => ({ ...prev, [k]: v }))
  }

  function selectPreset(idx: number) {
    setPresetIdx(idx)
    if (RRULE_PRESETS[idx].value) {
      set('recurrence', RRULE_PRESETS[idx].value)
    }
  }

  async function save() {
    const payload = { ...form, recurrence: presetIdx === RRULE_PRESETS.length - 1 ? customRrule : form.recurrence }
    if (isEdit) {
      await update.mutateAsync(payload)
    } else {
      await create.mutateAsync(payload)
    }
    onClose()
  }

  const isPending = create.isPending || update.isPending
  const isError   = create.isError   || update.isError

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.5)' }}>
      <div className="w-full max-w-lg rounded-2xl p-6 overflow-y-auto" style={{ background: 'var(--bg-card)', maxHeight: '90vh' }}>
        <h2 className="text-base font-semibold mb-4" style={{ color: 'var(--color-text)' }}>
          {isEdit ? 'Edit Mailing Job' : 'New Mailing Job'}
        </h2>

        <div className="space-y-3">
          {/* Basic fields */}
          {(
            [
              { label: 'Job Name',       key: 'name'            as const, ph: 'Monthly Loan Summary' },
              { label: 'Report Name',    key: 'reportName'      as const, ph: 'ActiveLoans' },
              { label: 'Recipients',     key: 'emailRecipients' as const, ph: 'a@bank.com, b@bank.com' },
              { label: 'Email Subject',  key: 'emailSubject'    as const, ph: 'Monthly Report' },
            ]
          ).map(({ label, key, ph }) => (
            <div key={key}>
              <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>
                {label}
              </label>
              <input
                type="text"
                value={form[key] as string}
                onChange={e => set(key, e.target.value)}
                placeholder={ph}
                className="w-full px-2 py-1.5 rounded text-sm outline-none"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
              />
            </div>
          ))}

          {/* Message */}
          <div>
            <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>
              Email Message (optional)
            </label>
            <textarea
              rows={2}
              value={form.emailMessage ?? ''}
              onChange={e => set('emailMessage', e.target.value)}
              className="w-full px-2 py-1.5 rounded text-sm outline-none resize-y"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
            />
          </div>

          {/* Output type chips */}
          <div>
            <label className="block text-xs mb-2 font-medium" style={{ color: 'var(--color-muted)' }}>
              Output Format
            </label>
            <div className="flex gap-2">
              {OUTPUT_TYPES.map(t => (
                <button
                  key={t}
                  onClick={() => set('outputType', t)}
                  className="text-xs px-3 py-1.5 rounded-full font-medium"
                  style={{
                    background: form.outputType === t ? 'var(--color-primary)' : 'var(--bg-subtle)',
                    color: form.outputType === t ? '#fff' : 'var(--color-muted)',
                    border: '1px solid var(--color-border)',
                  }}
                >
                  {t}
                </button>
              ))}
            </div>
          </div>

          {/* Schedule presets */}
          <div>
            <label className="block text-xs mb-2 font-medium" style={{ color: 'var(--color-muted)' }}>
              Schedule
            </label>
            <div className="flex flex-wrap gap-2 mb-2">
              {RRULE_PRESETS.map((p, i) => (
                <button
                  key={p.label}
                  onClick={() => selectPreset(i)}
                  className="text-xs px-3 py-1.5 rounded-full"
                  style={{
                    background: presetIdx === i ? 'var(--color-primary)' : 'var(--bg-subtle)',
                    color: presetIdx === i ? '#fff' : 'var(--color-muted)',
                    border: '1px solid var(--color-border)',
                  }}
                >
                  {p.label}
                </button>
              ))}
            </div>
            {presetIdx === RRULE_PRESETS.length - 1 && (
              <input
                type="text"
                value={customRrule}
                onChange={e => setCustomRrule(e.target.value)}
                placeholder="FREQ=WEEKLY;BYDAY=MO,WE"
                className="w-full px-2 py-1.5 rounded text-sm font-mono outline-none"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
              />
            )}
            {presetIdx < RRULE_PRESETS.length - 1 && (
              <p className="text-xs font-mono mt-1" style={{ color: 'var(--color-muted)' }}>
                {form.recurrence}
              </p>
            )}
          </div>
        </div>

        {isError && (
          <p className="text-xs mt-3" style={{ color: 'var(--color-error)' }}>
            Failed to save mailing job.
          </p>
        )}

        <div className="flex justify-end gap-3 mt-5">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
            Cancel
          </button>
          <button onClick={save} disabled={isPending}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {isPending ? 'Saving…' : isEdit ? 'Update Job' : 'Create Job'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function ReportMailingPage() {
  const { data, isLoading } = useMailingJobs()
  const jobs: ReportMailingJob[] = (data as { data?: ReportMailingJob[] } | undefined)?.data ?? []

  const [creating, setCreating] = useState(false)
  const [editing, setEditing]   = useState<ReportMailingJob | null>(null)
  const [deleting, setDeleting] = useState<ReportMailingJob | null>(null)

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Report Mailing Jobs"
        subtitle="Scheduled report delivery via email"
        actions={
          <button
            onClick={() => setCreating(true)}
            className="text-xs px-3 py-1.5 rounded-lg text-white"
            style={{ background: 'var(--color-primary)' }}
          >
            + New Mailing Job
          </button>
        }
      />

      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
              {['Name', 'Report', 'Recipients', 'Schedule', 'Format', 'Last Run', 'Status', 'Runs', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                  style={{ color: 'var(--color-muted)' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {jobs.length === 0 && (
              <tr>
                <td colSpan={9} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                  No mailing jobs configured.
                </td>
              </tr>
            )}
            {jobs.map(j => (
              <tr key={j.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                <td className="px-4 py-3 font-medium text-sm" style={{ color: 'var(--color-text)' }}>
                  {j.name}
                </td>
                <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>
                  {j.reportName}
                </td>
                <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)', maxWidth: 180 }}>
                  <span className="truncate block">{j.emailRecipients}</span>
                </td>
                <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>
                  {j.recurrence ?? '—'}
                </td>
                <td className="px-4 py-3">
                  <StatusBadge label={j.outputType} variant={outputVariant(j.outputType)} />
                </td>
                <td className="px-4 py-3 tabular-nums text-xs" style={{ color: 'var(--color-muted)' }}>
                  {formatTs(j.previousRunStartTime)}
                </td>
                <td className="px-4 py-3">
                  {j.previousRunStatus
                    ? <StatusBadge
                        label={j.previousRunStatus}
                        variant={j.previousRunStatus === 'SUCCESS' ? 'success' : j.previousRunStatus === 'FAILED' ? 'error' : 'neutral'}
                      />
                    : <span className="text-xs" style={{ color: 'var(--color-muted)' }}>—</span>
                  }
                </td>
                <td className="px-4 py-3 tabular-nums text-xs" style={{ color: 'var(--color-muted)' }}>
                  {j.runCount}
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-1.5">
                    <RunNowButton id={j.id} />
                    <button
                      onClick={() => setEditing(j)}
                      className="text-xs px-2 py-1 rounded"
                      style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => setDeleting(j)}
                      className="text-xs px-2 py-1 rounded"
                      style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}
                    >
                      Delete
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Delete confirm panel */}
      {deleting && (
        <div className="mt-4 rounded-xl p-5" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <p className="text-sm font-semibold" style={{ color: 'var(--color-text)' }}>
            Delete <span className="font-mono">{deleting.name}</span>?
          </p>
          <p className="text-xs mt-1" style={{ color: 'var(--color-muted)' }}>This action cannot be undone.</p>
          <DeleteMailingRow id={deleting.id} onDone={() => setDeleting(null)} />
        </div>
      )}

      {creating && <MailingJobModal onClose={() => setCreating(false)} />}
      {editing  && <MailingJobModal initial={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
