// web-react/src/app/features/groups/CenterDetailPage.tsx
import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { useCenter, useActivateCenter, useCenterGroups, useCenterMembers } from './api/useGroups'
import type { Center, CenterGroup, CenterMember } from './api/types'

type Tab = 'groups' | 'members'

export default function CenterDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [tab, setTab] = useState<Tab>('groups')

  const { data: centerData, isLoading } = useCenter(id!)
  const center: Center | undefined = (centerData as { data?: Center } | undefined)?.data

  const { data: groupsData }  = useCenterGroups(id!)
  const { data: membersData } = useCenterMembers(id!)

  const groups:  CenterGroup[]  = (groupsData  as { data?: CenterGroup[]  } | undefined)?.data ?? []
  const members: CenterMember[] = (membersData as { data?: CenterMember[] } | undefined)?.data ?? []

  const activate = useActivateCenter(id!)

  if (isLoading || !center) return (
    <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
  )

  const TABS: { key: Tab; label: string }[] = [
    { key: 'groups',  label: `Groups (${groups.length})`   },
    { key: 'members', label: `All Members (${members.length})` },
  ]

  return (
    <div>
      <PageHeader
        title={center.name}
        subtitle={`${center.officeName}${center.staffName ? ` · ${center.staffName}` : ''}`}
        actions={
          center.status === 'PENDING' ? (
            <button
              onClick={() => activate.mutateAsync()}
              disabled={activate.isPending}
              className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-success)' }}
            >
              {activate.isPending ? 'Activating…' : 'Activate Center'}
            </button>
          ) : undefined
        }
      />

      <div className="flex gap-3 mb-5">
        <StatusBadge
          label={center.status}
          variant={center.status === 'ACTIVE' ? 'success' : center.status === 'PENDING' ? 'warning' : 'neutral'}
        />
        {center.activationDate && (
          <span className="text-xs" style={{ color: 'var(--color-muted)' }}>
            Activated {new Date(center.activationDate).toLocaleDateString()}
          </span>
        )}
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-5 p-1 rounded-xl w-fit"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)' }}>
        {TABS.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className="text-xs px-4 py-1.5 rounded-lg transition-colors"
            style={{
              background: tab === t.key ? 'var(--bg-card)' : 'transparent',
              color:      tab === t.key ? 'var(--color-text)' : 'var(--color-muted)',
              fontWeight: tab === t.key ? 600 : 400,
              border:     tab === t.key ? '1px solid var(--color-border)' : '1px solid transparent',
            }}>
            {t.label}
          </button>
        ))}
      </div>

      {/* Groups tab */}
      {tab === 'groups' && (
        <div className="space-y-2">
          {groups.length === 0 && (
            <p className="text-xs py-6 text-center" style={{ color: 'var(--color-muted)' }}>
              No groups attached to this center.
            </p>
          )}
          {groups.map(g => (
            <Link
              key={g.id}
              to={`/groups/${g.id}`}
              className="flex items-center justify-between rounded-xl px-4 py-3 transition-colors hover:brightness-95"
              style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', textDecoration: 'none' }}
            >
              <span className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>{g.name}</span>
              <StatusBadge
                label={g.status}
                variant={g.status === 'ACTIVE' ? 'success' : g.status === 'PENDING' ? 'warning' : 'neutral'}
              />
            </Link>
          ))}
        </div>
      )}

      {/* All Members tab */}
      {tab === 'members' && (
        <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                {['Name', 'Account No', 'Group'].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                    style={{ color: 'var(--color-muted)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {members.length === 0 && (
                <tr><td colSpan={3} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                  No members found.
                </td></tr>
              )}
              {members.map(m => (
                <tr key={m.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                  <td className="px-4 py-3 font-medium text-sm" style={{ color: 'var(--color-text)' }}>{m.displayName}</td>
                  <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>{m.accountNo}</td>
                  <td className="px-4 py-3">
                    <Link
                      to={`/groups/${m.groupId}`}
                      className="text-xs"
                      style={{ color: 'var(--color-primary)' }}
                    >
                      {m.groupName}
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
