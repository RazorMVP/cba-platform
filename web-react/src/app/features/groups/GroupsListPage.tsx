// web-react/src/app/features/groups/GroupsListPage.tsx
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { useGroups, useCreateGroup } from './api/useGroups'
import type { Group, GroupStatus, CreateGroupRequest } from './api/types'

function statusVariant(s: GroupStatus): 'warning' | 'success' | 'neutral' {
  if (s === 'ACTIVE')  return 'success'
  if (s === 'PENDING') return 'warning'
  return 'neutral'
}

function CreateGroupModal({ onClose }: { onClose: () => void }) {
  const create = useCreateGroup()
  const [form, setForm] = useState<CreateGroupRequest>({ name: '', officeId: '', externalId: '', staffId: '' })

  async function save() {
    await create.mutateAsync({
      name: form.name,
      officeId: form.officeId,
      externalId: form.externalId || undefined,
      staffId: form.staffId || undefined,
    })
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.5)' }}>
      <div className="w-full max-w-md rounded-2xl p-6" style={{ background: 'var(--bg-card)' }}>
        <h2 className="text-base font-semibold mb-4" style={{ color: 'var(--color-text)' }}>New Group</h2>
        <div className="space-y-3">
          {([
            { label: 'Group Name', key: 'name'       as const, required: true  },
            { label: 'Office ID',  key: 'officeId'   as const, required: true  },
            { label: 'External ID',key: 'externalId' as const, required: false },
            { label: 'Staff ID',   key: 'staffId'    as const, required: false },
          ]).map(({ label, key, required }) => (
            <div key={key}>
              <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>
                {label}{required && ' *'}
              </label>
              <input
                type="text"
                value={form[key] ?? ''}
                onChange={e => setForm(prev => ({ ...prev, [key]: e.target.value }))}
                className="w-full px-2 py-1.5 rounded text-sm outline-none"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
              />
            </div>
          ))}
        </div>
        {create.isError && (
          <p className="text-xs mt-3" style={{ color: 'var(--color-error)' }}>Failed to create group.</p>
        )}
        <div className="flex justify-end gap-3 mt-5">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button onClick={save} disabled={create.isPending || !form.name || !form.officeId}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {create.isPending ? 'Creating…' : 'Create Group'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function GroupsListPage() {
  const navigate = useNavigate()
  const { data, isLoading } = useGroups()
  const groups: Group[] = (data as { data?: Group[] } | undefined)?.data ?? []

  const [creating,     setCreating]     = useState(false)
  const [search,       setSearch]       = useState('')
  const [statusFilter, setStatusFilter] = useState<GroupStatus | ''>('')

  const filtered = groups.filter(g => {
    if (statusFilter && g.status !== statusFilter) return false
    if (search) {
      const q = search.toLowerCase()
      return g.name.toLowerCase().includes(q) || g.officeName.toLowerCase().includes(q)
    }
    return true
  })

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Groups"
        subtitle="Microfinance lending groups"
        actions={
          <button onClick={() => setCreating(true)}
            className="text-xs px-3 py-1.5 rounded-lg text-white"
            style={{ background: 'var(--color-primary)' }}>
            + New Group
          </button>
        }
      />

      <div className="flex gap-3 mb-4">
        <input
          type="text"
          placeholder="Search groups…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="px-3 py-2 rounded-lg text-sm outline-none"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)', width: 240 }}
        />
        <select
          value={statusFilter}
          onChange={e => setStatusFilter(e.target.value as GroupStatus | '')}
          className="px-3 py-2 rounded-lg text-sm outline-none"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
        >
          <option value="">All statuses</option>
          <option value="PENDING">Pending</option>
          <option value="ACTIVE">Active</option>
          <option value="CLOSED">Closed</option>
        </select>
      </div>

      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
              {['Name', 'Office', 'Staff', 'Activated', 'Status'].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                  style={{ color: 'var(--color-muted)' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 && (
              <tr><td colSpan={5} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                No groups found.
              </td></tr>
            )}
            {filtered.map(g => (
              <tr
                key={g.id}
                onClick={() => navigate(`/groups/${g.id}`)}
                className="cursor-pointer hover:brightness-95 transition-all"
                style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}
              >
                <td className="px-4 py-3 font-medium text-sm" style={{ color: 'var(--color-text)' }}>{g.name}</td>
                <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>{g.officeName}</td>
                <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>{g.staffName ?? '—'}</td>
                <td className="px-4 py-3 text-xs tabular-nums" style={{ color: 'var(--color-muted)' }}>
                  {g.activationDate ? new Date(g.activationDate).toLocaleDateString() : '—'}
                </td>
                <td className="px-4 py-3">
                  <StatusBadge label={g.status} variant={statusVariant(g.status)} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {creating && <CreateGroupModal onClose={() => setCreating(false)} />}
    </div>
  )
}
