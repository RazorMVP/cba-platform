// web-react/src/app/features/reports/CobSchedulerPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { useCobJobs, useCobJobHistory, useRunCobJob } from './api/useReports'
import type { CobJob, CobJobHistory, JobStatus } from './api/types'

// The 3 hardcoded CoB job names that exist in the backend
const COB_JOB_NAMES = [
  'standingOrderExecutionJob',
  'interestAccrualJob',
  'arrearsClassificationJob',
] as const

function statusVariant(s: JobStatus | string | undefined): 'success' | 'error' | 'warning' | 'neutral' {
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED')  return 'error'
  if (s === 'RUNNING') return 'warning'
  return 'neutral'
}

function formatDuration(ms: number | undefined): string {
  if (ms === undefined || ms === null) return '—'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`
  const m = Math.floor(ms / 60_000)
  const s = Math.round((ms % 60_000) / 1000)
  return `${m}m ${s}s`
}

function formatTs(ts: string | undefined): string {
  if (!ts) return '—'
  return new Date(ts).toLocaleString()
}

// ── Run Job button (hook at top level) ───────────────────────────────────────

function RunJobButton({ jobName }: { jobName: string }) {
  const run = useRunCobJob(jobName)
  return (
    <button
      onClick={() => run.mutate()}
      disabled={run.isPending}
      className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
      style={{ background: 'var(--color-primary)' }}
    >
      {run.isPending ? 'Triggering…' : 'Run Now'}
    </button>
  )
}

// ── History panel ─────────────────────────────────────────────────────────────

function HistoryPanel({ jobName }: { jobName: string }) {
  const { data, isLoading } = useCobJobHistory(jobName)
  const history: CobJobHistory[] = (data as { data?: CobJobHistory[] } | undefined)?.data ?? []

  if (isLoading) return (
    <p className="text-xs py-2" style={{ color: 'var(--color-muted)' }}>Loading history…</p>
  )

  if (history.length === 0) return (
    <p className="text-xs py-2" style={{ color: 'var(--color-muted)' }}>No history yet.</p>
  )

  return (
    <div className="rounded-xl overflow-hidden mt-3" style={{ border: '1px solid var(--color-border)' }}>
      <table className="w-full text-xs border-collapse">
        <thead>
          <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
            {['Started', 'Ended', 'Duration', 'Status', 'Error'].map(h => (
              <th key={h} className="px-3 py-2 text-left font-semibold uppercase tracking-wider"
                style={{ color: 'var(--color-muted)' }}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {history.slice(0, 20).map(h => (
            <tr key={h.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
              <td className="px-3 py-2 tabular-nums" style={{ color: 'var(--color-text)' }}>
                {formatTs(h.startTime)}
              </td>
              <td className="px-3 py-2 tabular-nums" style={{ color: 'var(--color-text)' }}>
                {formatTs(h.endTime)}
              </td>
              <td className="px-3 py-2 tabular-nums" style={{ color: 'var(--color-muted)' }}>
                {formatDuration(h.duration)}
              </td>
              <td className="px-3 py-2">
                <StatusBadge label={h.status} variant={statusVariant(h.status)} />
              </td>
              <td className="px-3 py-2 text-xs font-mono" style={{ color: 'var(--color-error)', maxWidth: 280 }}>
                {h.errorMessage ?? '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ── Job card ──────────────────────────────────────────────────────────────────

function JobCard({ job }: { job: CobJob }) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div className="rounded-xl p-5" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
      {/* Header row */}
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <p className="text-sm font-semibold truncate" style={{ color: 'var(--color-text)' }}>
              {job.displayName}
            </p>
            {job.currentlyRunning && (
              <StatusBadge label="RUNNING" variant="warning" />
            )}
          </div>
          <p className="text-xs font-mono" style={{ color: 'var(--color-muted)' }}>
            {job.jobName}
          </p>
        </div>
        <RunJobButton jobName={job.jobName} />
      </div>

      {/* Stats grid */}
      <div className="grid grid-cols-2 gap-x-6 gap-y-2 mt-4 text-xs">
        <div>
          <span style={{ color: 'var(--color-muted)' }}>Cron Schedule</span>
          <p className="font-mono mt-0.5" style={{ color: 'var(--color-text)' }}>
            {job.cronExpression}
          </p>
        </div>
        <div>
          <span style={{ color: 'var(--color-muted)' }}>Next Run</span>
          <p className="tabular-nums mt-0.5" style={{ color: 'var(--color-text)' }}>
            {formatTs(job.nextRunTime)}
          </p>
        </div>
        <div>
          <span style={{ color: 'var(--color-muted)' }}>Last Run</span>
          <p className="tabular-nums mt-0.5" style={{ color: 'var(--color-text)' }}>
            {formatTs(job.lastRunStartTime)}
          </p>
        </div>
        <div>
          <span style={{ color: 'var(--color-muted)' }}>Last Status</span>
          <div className="mt-0.5">
            {job.lastRunStatus
              ? <StatusBadge label={job.lastRunStatus} variant={statusVariant(job.lastRunStatus)} />
              : <span style={{ color: 'var(--color-muted)' }}>—</span>
            }
          </div>
        </div>
      </div>

      {/* History toggle */}
      <button
        onClick={() => setExpanded(e => !e)}
        className="mt-4 text-xs underline"
        style={{ color: 'var(--color-primary)' }}
      >
        {expanded ? 'Hide history' : 'Show history'}
      </button>

      {expanded && <HistoryPanel jobName={job.jobName} />}
    </div>
  )
}

// ── Placeholder card for jobs not yet in DB ───────────────────────────────────

function PlaceholderJobCard({ jobName }: { jobName: string }) {
  return (
    <div className="rounded-xl p-5 opacity-60" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
      <p className="text-sm font-semibold" style={{ color: 'var(--color-text)' }}>{jobName}</p>
      <p className="text-xs mt-1" style={{ color: 'var(--color-muted)' }}>
        Job not yet registered. Run the CoB migration to initialise.
      </p>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function CobSchedulerPage() {
  const { data, isLoading } = useCobJobs()
  const jobs: CobJob[] = (data as { data?: CobJob[] } | undefined)?.data ?? []

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  // Map fetched jobs by name for O(1) lookup
  const jobMap = new Map(jobs.map(j => [j.jobName, j]))

  return (
    <div>
      <PageHeader
        title="CoB Scheduler"
        subtitle="Close of Business batch jobs — nightly processing"
      />

      {/* Info banner */}
      <div className="flex items-start gap-3 p-4 mb-6 rounded-xl text-xs"
        style={{ background: 'var(--color-info-bg)', color: 'var(--color-info)', border: '1px solid var(--color-border)' }}>
        <span className="mt-0.5">ℹ</span>
        <span>
          Jobs run automatically each night: Standing Orders at 23:55 → Interest Accrual at 23:57 → Arrears Classification at 23:59.
          Use <strong>Run Now</strong> to trigger a job outside the schedule (e.g. for testing or recovery).
        </span>
      </div>

      <div className="flex flex-col gap-4">
        {COB_JOB_NAMES.map(name => {
          const job = jobMap.get(name)
          return job
            ? <JobCard key={name} job={job} />
            : <PlaceholderJobCard key={name} jobName={name} />
        })}
      </div>
    </div>
  )
}
