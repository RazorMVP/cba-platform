// web-react/src/app/features/groups/GroupDetailPage.tsx
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import {
  useGroup, useActivateGroup, useGroupMembers,
  useAddGroupMember, useRemoveGroupMember,
  useGlimAccounts, useGenerateCollectionSheet,
  useAssignGroupStaff, useUnassignGroupStaff,
} from './api/useGroups'
import type { Group, GroupMember, GlimAccount, CollectionSheetItem } from './api/types'

type Tab = 'members' | 'collection' | 'glim' | 'staff'

// ── Remove member (hook at component level) ────────────────────────────────────
function RemoveMemberButton({ groupId, member, onDone }: {
  groupId: string
  member: GroupMember
  onDone: () => void
}) {
  const remove = useRemoveGroupMember(groupId)
  return (
    <button
      onClick={async () => { await remove.mutateAsync(member.id); onDone() }}
      disabled={remove.isPending}
      className="text-xs px-2 py-1 rounded disabled:opacity-60"
      style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}
    >
      {remove.isPending ? '…' : 'Remove'}
    </button>
  )
}

export default function GroupDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [tab, setTab] = useState<Tab>('members')
  const [meetingDate, setMeetingDate] = useState(new Date().toISOString().slice(0, 10))
  const [addMemberId, setAddMemberId] = useState('')
  const [staffId, setStaffId] = useState('')
  const [sheet, setSheet] = useState<CollectionSheetItem[] | null>(null)

  const { data: groupData, isLoading } = useGroup(id!)
  const group: Group | undefined = (groupData as { data?: Group } | undefined)?.data

  const { data: membersData, refetch: refetchMembers } = useGroupMembers(id!)
  const members: GroupMember[] = (membersData as { data?: GroupMember[] } | undefined)?.data ?? []

  const { data: glimData } = useGlimAccounts(id!)
  const glimAccounts: GlimAccount[] = (glimData as { data?: GlimAccount[] } | undefined)?.data ?? []

  const activate  = useActivateGroup(id!)
  const addMember = useAddGroupMember(id!)
  const genSheet  = useGenerateCollectionSheet()
  const assignStaff   = useAssignGroupStaff(id!)
  const unassignStaff = useUnassignGroupStaff(id!)

  if (isLoading || !group) return (
    <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
  )

  const TABS: { key: Tab; label: string }[] = [
    { key: 'members',    label: 'Members'          },
    { key: 'collection', label: 'Collection Sheet' },
    { key: 'glim',       label: 'GLIM Accounts'    },
    { key: 'staff',      label: 'Staff'            },
  ]

  return (
    <div>
      <PageHeader
        title={group.name}
        subtitle={`${group.officeName} · ${group.staffName ?? 'No staff assigned'}`}
        actions={
          group.status === 'PENDING' ? (
            <button
              onClick={() => activate.mutateAsync()}
              disabled={activate.isPending}
              className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-success)' }}
            >
              {activate.isPending ? 'Activating…' : 'Activate Group'}
            </button>
          ) : undefined
        }
      />

      {/* Status + meta */}
      <div className="flex gap-3 mb-5">
        <StatusBadge
          label={group.status}
          variant={group.status === 'ACTIVE' ? 'success' : group.status === 'PENDING' ? 'warning' : 'neutral'}
        />
        {group.activationDate && (
          <span className="text-xs" style={{ color: 'var(--color-muted)' }}>
            Activated {new Date(group.activationDate).toLocaleDateString()}
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

      {/* Members tab */}
      {tab === 'members' && (
        <div>
          <div className="flex gap-2 mb-4">
            <input
              type="text"
              placeholder="Customer ID to add…"
              value={addMemberId}
              onChange={e => setAddMemberId(e.target.value)}
              className="px-2 py-1.5 rounded text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)', width: 220 }}
            />
            <button
              onClick={async () => {
                if (!addMemberId) return
                await addMember.mutateAsync(addMemberId)
                setAddMemberId('')
                refetchMembers()
              }}
              disabled={addMember.isPending || !addMemberId}
              className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}
            >
              {addMember.isPending ? '…' : '+ Add Member'}
            </button>
          </div>
          <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                  {['Name', 'Account No', ''].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                      style={{ color: 'var(--color-muted)' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {members.length === 0 && (
                  <tr><td colSpan={3} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                    No members yet.
                  </td></tr>
                )}
                {members.map(m => (
                  <tr key={m.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                    <td className="px-4 py-3 font-medium text-sm" style={{ color: 'var(--color-text)' }}>{m.displayName}</td>
                    <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>{m.accountNo}</td>
                    <td className="px-4 py-3">
                      <RemoveMemberButton groupId={id!} member={m} onDone={refetchMembers} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Collection Sheet tab */}
      {tab === 'collection' && (
        <div>
          <div className="flex gap-2 mb-5 items-end">
            <div>
              <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>Meeting Date</label>
              <input
                type="date"
                value={meetingDate}
                onChange={e => setMeetingDate(e.target.value)}
                className="px-2 py-1.5 rounded text-sm outline-none"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
              />
            </div>
            <button
              onClick={async () => {
                const result = await genSheet.mutateAsync({ groupId: id!, meetingDate })
                const items = (result as { data?: { items?: CollectionSheetItem[] } } | undefined)?.data?.items ?? []
                setSheet(items)
              }}
              disabled={genSheet.isPending}
              className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}
            >
              {genSheet.isPending ? 'Generating…' : 'Generate Sheet'}
            </button>
          </div>
          {sheet && (
            <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                    {['Client', 'Loan Account', 'Due Amount', 'Paid Amount'].map(h => (
                      <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                        style={{ color: 'var(--color-muted)' }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {sheet.length === 0 && (
                    <tr><td colSpan={4} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                      No items for this meeting date.
                    </td></tr>
                  )}
                  {sheet.map((item, i) => (
                    <tr key={i} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                      <td className="px-4 py-3 text-sm" style={{ color: 'var(--color-text)' }}>{item.clientName}</td>
                      <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>{item.loanAccountNo}</td>
                      <td className="px-4 py-3 text-xs tabular-nums" style={{ color: 'var(--color-text)' }}>
                        {item.dueAmount.toFixed(2)}
                      </td>
                      <td className="px-4 py-3 text-xs tabular-nums" style={{ color: 'var(--color-success)' }}>
                        {item.paidAmount.toFixed(2)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* GLIM Accounts tab */}
      {tab === 'glim' && (
        <div className="space-y-3">
          {glimAccounts.length === 0 && (
            <p className="text-xs py-6 text-center" style={{ color: 'var(--color-muted)' }}>No GLIM accounts for this group.</p>
          )}
          {glimAccounts.map(g => (
            <div key={g.id} className="rounded-xl p-4"
              style={{ border: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
              <div className="flex justify-between items-start">
                <div>
                  <p className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>{g.clientName}</p>
                  <p className="text-xs font-mono mt-0.5" style={{ color: 'var(--color-muted)' }}>{g.accountNo}</p>
                </div>
                <StatusBadge label={g.status} variant="info" />
              </div>
              <div className="flex gap-6 mt-3">
                <div>
                  <p className="text-xs" style={{ color: 'var(--color-muted)' }}>Principal</p>
                  <p className="text-sm tabular-nums font-medium" style={{ color: 'var(--color-text)' }}>
                    {g.principalAmount.toFixed(2)}
                  </p>
                </div>
                <div>
                  <p className="text-xs" style={{ color: 'var(--color-muted)' }}>Outstanding</p>
                  <p className="text-sm tabular-nums font-medium" style={{ color: 'var(--color-text)' }}>
                    {g.outstandingBalance.toFixed(2)}
                  </p>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Staff tab */}
      {tab === 'staff' && (
        <div>
          <div className="rounded-xl p-5 mb-4" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
            <p className="text-xs font-semibold mb-1" style={{ color: 'var(--color-muted)' }}>Assigned Loan Officer</p>
            <p className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>
              {group.staffName ?? '— None —'}
            </p>
          </div>
          <div className="flex gap-2 items-end">
            <div>
              <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>
                Assign Staff ID
              </label>
              <input
                type="text"
                value={staffId}
                onChange={e => setStaffId(e.target.value)}
                placeholder="Staff UUID…"
                className="px-2 py-1.5 rounded text-sm outline-none"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)', width: 240 }}
              />
            </div>
            <button
              onClick={async () => { await assignStaff.mutateAsync(staffId); setStaffId('') }}
              disabled={assignStaff.isPending || !staffId}
              className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}
            >
              {assignStaff.isPending ? '…' : 'Assign'}
            </button>
            {group.staffId && (
              <button
                onClick={() => unassignStaff.mutateAsync()}
                disabled={unassignStaff.isPending}
                className="text-xs px-3 py-1.5 rounded-lg disabled:opacity-60"
                style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}
              >
                {unassignStaff.isPending ? '…' : 'Unassign'}
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
