// web-react/src/app/features/admin/MakerCheckerPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import {
  useMakerCheckerEntries,
  useApproveMakerChecker,
  useRejectMakerChecker,
} from './api/useAdmin'
import type { MakerCheckerEntry, MakerCheckerStatus } from './api/types'

const TABS: { label: string; value: MakerCheckerStatus | undefined }[] = [
  { label: 'All',      value: undefined   },
  { label: 'Pending',  value: 'PENDING'   },
  { label: 'Approved', value: 'APPROVED'  },
  { label: 'Rejected', value: 'REJECTED'  },
]

function statusVariant(s: MakerCheckerStatus): 'warning' | 'success' | 'error' {
  if (s === 'PENDING')  return 'warning'
  if (s === 'APPROVED') return 'success'
  return 'error'
}

// ── Action buttons (hooks at top level per row) ───────────────────────────────

function ActionRow({ entry, onDone }: { entry: MakerCheckerEntry; onDone: () => void }) {
  const approve = useApproveMakerChecker(entry.id)
  const reject  = useRejectMakerChecker(entry.id)

  if (entry.status !== 'PENDING') return null

  return (
    <div className="flex gap-1.5">
      <button
        onClick={async () => { await approve.mutateAsync(); onDone() }}
        disabled={approve.isPending || reject.isPending}
        className="text-xs px-2 py-1 rounded disabled:opacity-60 text-white"
        style={{ background: 'var(--color-success)' }}
      >
        {approve.isPending ? '…' : 'Approve'}
      </button>
      <button
        onClick={async () => { await reject.mutateAsync(); onDone() }}
        disabled={approve.isPending || reject.isPending}
        className="text-xs px-2 py-1 rounded disabled:opacity-60"
        style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}
      >
        {reject.isPending ? '…' : 'Reject'}
      </button>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function MakerCheckerPage() {
  const [activeTab, setActiveTab] = useState<MakerCheckerStatus | undefined>(undefined)

  const { data, isLoading, refetch } = useMakerCheckerEntries(activeTab)
  const entries: MakerCheckerEntry[] =
    (data as { data?: MakerCheckerEntry[] } | undefined)?.data ?? []

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Maker-Checker"
        subtitle="Two-person approval workflow for sensitive operations"
      />

      {/* Status filter tabs */}
      <div className="flex gap-1 mb-5 p-1 rounded-xl w-fit"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)' }}>
        {TABS.map(tab => (
          <button
            key={tab.label}
            onClick={() => setActiveTab(tab.value)}
            className="text-xs px-4 py-1.5 rounded-lg transition-colors"
            style={{
              background: activeTab === tab.value ? 'var(--bg-card)' : 'transparent',
              color:      activeTab === tab.value ? 'var(--color-text)' : 'var(--color-muted)',
              fontWeight: activeTab === tab.value ? 600 : 400,
              border:     activeTab === tab.value ? '1px solid var(--color-border)' : '1px solid transparent',
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
              {['Entity Type', 'Action', 'Resource', 'Made By', 'Date', 'Checked By', 'Status', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                  style={{ color: 'var(--color-muted)' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {entries.length === 0 && (
              <tr><td colSpan={8} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                No entries found.
              </td></tr>
            )}
            {entries.map(e => (
              <tr key={e.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>
                  {e.entityType}
                </td>
                <td className="px-4 py-3 text-xs font-medium" style={{ color: 'var(--color-text)' }}>
                  {e.actionName}
                </td>
                <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>
                  {e.resourceId ?? '—'}
                </td>
                <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>
                  {e.madeByUsername}
                </td>
                <td className="px-4 py-3 text-xs tabular-nums" style={{ color: 'var(--color-muted)' }}>
                  {new Date(e.madeOnDate).toLocaleDateString()}
                </td>
                <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>
                  {e.checkedByUsername ?? '—'}
                </td>
                <td className="px-4 py-3">
                  <StatusBadge label={e.status} variant={statusVariant(e.status)} />
                </td>
                <td className="px-4 py-3">
                  <ActionRow entry={e} onDone={() => refetch()} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
