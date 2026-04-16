// web-react/src/app/features/reports/ReportsListPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { useReports, useCreateReport, useDeleteReport, useRunReport } from './api/useReports'
import type { Report, ReportRequest, ReportRow } from './api/types'

const CATEGORIES = ['All', 'Core', 'Self Service', 'User']

// ── Delete row (hook must be at component top level) ─────────────────────────

function DeleteRow({ report, onConfirm }: { report: Report; onConfirm: (r: Report) => void }) {
  return (
    <button
      onClick={() => onConfirm(report)}
      className="text-xs px-2 py-1 rounded"
      style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}
    >
      Delete
    </button>
  )
}

function DeleteAction({ id, onDone }: { id: string; onDone: () => void }) {
  const del = useDeleteReport(id)
  return (
    <div className="flex justify-end gap-3 mt-4">
      <button
        onClick={onDone}
        className="text-xs px-3 py-1.5 rounded-lg"
        style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
      >
        Cancel
      </button>
      <button
        onClick={async () => { await del.mutateAsync(); onDone() }}
        disabled={del.isPending}
        className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
        style={{ background: 'var(--color-error)' }}
      >
        {del.isPending ? 'Deleting…' : 'Delete Report'}
      </button>
    </div>
  )
}

// ── Run-report panel ──────────────────────────────────────────────────────────

function RunReportPanel({
  report,
  onClose,
}: {
  report: Report
  onClose: () => void
}) {
  const params = report.reportParameters ?? []
  const [values, setValues] = useState<Record<string, string>>(
    Object.fromEntries(params.map(p => [p.parameterName, p.defaultValue ?? '']))
  )
  const run = useRunReport(report.reportName)

  function setValue(name: string, val: string) {
    setValues(prev => ({ ...prev, [name]: val }))
  }

  const rows: ReportRow[] = (run.data as { data?: ReportRow[] } | undefined)?.data ?? []
  const columns = rows.length > 0 ? Object.keys(rows[0]) : []

  function exportCsv() {
    if (rows.length === 0) return
    const header = columns.join(',')
    const body = rows
      .map(r => columns.map(c => JSON.stringify(r[c] ?? '')).join(','))
      .join('\n')
    const blob = new Blob([header + '\n' + body], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${report.reportName}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="mt-4 rounded-xl p-5" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
      <div className="flex items-center justify-between mb-4">
        <p className="text-sm font-semibold" style={{ color: 'var(--color-text)' }}>
          Run — <span className="font-mono">{report.reportName}</span>
        </p>
        <button onClick={onClose} className="text-xs px-2 py-1 rounded" style={{ color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
          Close
        </button>
      </div>

      {/* Dynamic param form */}
      {params.length > 0 && (
        <div className="grid grid-cols-2 gap-3 mb-4">
          {params.map(p => (
            <div key={p.parameterId}>
              <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>
                {p.parameterLabel ?? p.parameterName}
              </label>
              <input
                type={p.parameterType === 'date' ? 'date' : p.parameterType === 'number' ? 'number' : 'text'}
                value={values[p.parameterName] ?? ''}
                onChange={e => setValue(p.parameterName, e.target.value)}
                className="w-full px-2 py-1.5 rounded text-sm outline-none"
                style={{
                  background: 'var(--bg-subtle)',
                  border: '1px solid var(--color-border)',
                  color: 'var(--color-text)',
                }}
              />
            </div>
          ))}
        </div>
      )}

      <div className="flex gap-2 mb-4">
        <button
          onClick={() => run.mutate(values)}
          disabled={run.isPending}
          className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
          style={{ background: 'var(--color-primary)' }}
        >
          {run.isPending ? 'Running…' : 'Run Report'}
        </button>
        {rows.length > 0 && (
          <button
            onClick={exportCsv}
            className="text-xs px-3 py-1.5 rounded-lg"
            style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
          >
            Export CSV
          </button>
        )}
      </div>

      {run.isError && (
        <p className="text-xs mb-3" style={{ color: 'var(--color-error)' }}>
          Report failed to run.
        </p>
      )}

      {/* Schema-on-read results table */}
      {rows.length > 0 && (
        <div className="rounded-xl overflow-auto" style={{ border: '1px solid var(--color-border)', maxHeight: 400 }}>
          <table className="w-full text-xs border-collapse min-w-max">
            <thead>
              <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                {columns.map(col => (
                  <th key={col} className="px-3 py-2 text-left font-semibold uppercase tracking-wider whitespace-nowrap"
                    style={{ color: 'var(--color-muted)' }}>
                    {col}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, i) => (
                <tr key={i} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                  {columns.map(col => (
                    <td key={col} className="px-3 py-2 tabular-nums whitespace-nowrap"
                      style={{ color: 'var(--color-text)' }}>
                      {String(row[col] ?? '—')}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {run.isSuccess && rows.length === 0 && (
        <p className="text-xs text-center py-4" style={{ color: 'var(--color-muted)' }}>
          No rows returned.
        </p>
      )}
    </div>
  )
}

// ── Create report modal ───────────────────────────────────────────────────────

function CreateReportModal({ onClose }: { onClose: () => void }) {
  const create = useCreateReport()
  const [form, setForm] = useState<ReportRequest>({
    reportName: '',
    reportType: 'Table',
    reportSubType: '',
    description: '',
    reportSql: '',
    coreReport: false,
    useReport: true,
  })

  function set<K extends keyof ReportRequest>(k: K, v: ReportRequest[K]) {
    setForm(prev => ({ ...prev, [k]: v }))
  }

  async function save() {
    await create.mutateAsync(form)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.5)' }}>
      <div className="w-full max-w-lg rounded-2xl p-6" style={{ background: 'var(--bg-card)' }}>
        <h2 className="text-base font-semibold mb-4" style={{ color: 'var(--color-text)' }}>
          Create Report
        </h2>

        <div className="space-y-3">
          {(
            [
              { label: 'Report Name', key: 'reportName', type: 'text' },
              { label: 'Report Type', key: 'reportType', type: 'text' },
              { label: 'Sub Type', key: 'reportSubType', type: 'text' },
              { label: 'Description', key: 'description', type: 'text' },
            ] as const
          ).map(({ label, key, type }) => (
            <div key={key}>
              <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>
                {label}
              </label>
              <input
                type={type}
                value={form[key] as string}
                onChange={e => set(key, e.target.value)}
                className="w-full px-2 py-1.5 rounded text-sm outline-none"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
              />
            </div>
          ))}

          <div>
            <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>
              Report SQL
            </label>
            <textarea
              rows={5}
              value={form.reportSql}
              onChange={e => set('reportSql', e.target.value)}
              placeholder="SELECT * FROM …"
              className="w-full px-2 py-1.5 rounded text-sm font-mono outline-none resize-y"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
            />
          </div>

          <div className="flex gap-4">
            <label className="flex items-center gap-2 text-xs cursor-pointer" style={{ color: 'var(--color-text)' }}>
              <input
                type="checkbox"
                checked={form.coreReport}
                onChange={e => set('coreReport', e.target.checked)}
              />
              Core Report
            </label>
            <label className="flex items-center gap-2 text-xs cursor-pointer" style={{ color: 'var(--color-text)' }}>
              <input
                type="checkbox"
                checked={form.useReport}
                onChange={e => set('useReport', e.target.checked)}
              />
              Visible to Users
            </label>
          </div>
        </div>

        {create.isError && (
          <p className="text-xs mt-3" style={{ color: 'var(--color-error)' }}>Failed to create report.</p>
        )}

        <div className="flex justify-end gap-3 mt-5">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
            Cancel
          </button>
          <button onClick={save} disabled={create.isPending}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {create.isPending ? 'Creating…' : 'Create Report'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function ReportsListPage() {
  const { data, isLoading } = useReports()
  const reports: Report[] = (data as { data?: Report[] } | undefined)?.data ?? []

  const [category, setCategory] = useState('All')
  const [search, setSearch]     = useState('')
  const [running, setRunning]   = useState<Report | null>(null)
  const [deleting, setDeleting] = useState<Report | null>(null)
  const [creating, setCreating] = useState(false)

  const filtered = reports.filter(r => {
    const matchCat =
      category === 'All'
        ? true
        : category === 'Core'
        ? r.coreReport
        : category === 'Self Service'
        ? r.selfServiceUserReport
        : !r.coreReport && !r.selfServiceUserReport
    const matchSearch = !search || r.reportName.toLowerCase().includes(search.toLowerCase())
    return matchCat && matchSearch
  })

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Reports"
        subtitle="Run and manage dynamic SQL reports"
        actions={
          <button
            onClick={() => setCreating(true)}
            className="text-xs px-3 py-1.5 rounded-lg text-white"
            style={{ background: 'var(--color-primary)' }}
          >
            + New Report
          </button>
        }
      />

      {/* Filters */}
      <div className="flex items-center gap-3 mb-4">
        <input
          type="text"
          placeholder="Search reports…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="px-3 py-1.5 rounded-lg text-sm outline-none w-64"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
        />
        <div className="flex gap-1">
          {CATEGORIES.map(c => (
            <button
              key={c}
              onClick={() => setCategory(c)}
              className="text-xs px-3 py-1.5 rounded-full"
              style={{
                background: category === c ? 'var(--color-primary)' : 'var(--bg-subtle)',
                color: category === c ? '#fff' : 'var(--color-muted)',
                border: '1px solid var(--color-border)',
              }}
            >
              {c}
            </button>
          ))}
        </div>
      </div>

      {/* Table */}
      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
              {['Report Name', 'Type', 'Sub Type', 'Parameters', 'Scope', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                  style={{ color: 'var(--color-muted)' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                  No reports found.
                </td>
              </tr>
            )}
            {filtered.map(r => (
              <tr key={r.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                <td className="px-4 py-3 font-medium text-sm" style={{ color: 'var(--color-text)' }}>
                  {r.reportName}
                </td>
                <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>{r.reportType}</td>
                <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>{r.reportSubType ?? '—'}</td>
                <td className="px-4 py-3 tabular-nums text-xs" style={{ color: 'var(--color-muted)' }}>
                  {r.reportParameters?.length ?? 0}
                </td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1">
                    {r.coreReport && <StatusBadge label="Core" variant="primary" />}
                    {r.selfServiceUserReport && <StatusBadge label="Self Service" variant="info" />}
                    {!r.coreReport && !r.selfServiceUserReport && <StatusBadge label="User" variant="neutral" />}
                  </div>
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-2">
                    <button
                      onClick={() => { setRunning(r); setDeleting(null) }}
                      className="text-xs px-2 py-1 rounded"
                      style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}
                    >
                      Run
                    </button>
                    {!r.coreReport && (
                      <DeleteRow report={r} onConfirm={r2 => { setDeleting(r2); setRunning(null) }} />
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Run report panel */}
      {running && (
        <RunReportPanel report={running} onClose={() => setRunning(null)} />
      )}

      {/* Delete confirm */}
      {deleting && (
        <div className="mt-4 rounded-xl p-5" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <p className="text-sm font-semibold" style={{ color: 'var(--color-text)' }}>
            Delete <span className="font-mono">{deleting.reportName}</span>?
          </p>
          <p className="text-xs mt-1" style={{ color: 'var(--color-muted)' }}>This action cannot be undone.</p>
          <DeleteAction id={deleting.id} onDone={() => setDeleting(null)} />
        </div>
      )}

      {creating && <CreateReportModal onClose={() => setCreating(false)} />}
    </div>
  )
}
