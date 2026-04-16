// web-react/src/app/features/admin/UsersPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import {
  useUsers, useCreateUser, useEnableUser, useDisableUser, useDeleteUser,
} from './api/useAdmin'
import { useRoles } from './api/useAdmin'
import { useOffices } from './api/useAdmin'
import type { PlatformUser, CreateUserRequest } from './api/types'

// ── Toggle enable/disable (hook at top level) ─────────────────────────────────

function ToggleUserRow({ user }: { user: PlatformUser }) {
  const enable  = useEnableUser(user.id)
  const disable = useDisableUser(user.id)
  const isPending = enable.isPending || disable.isPending

  return (
    <button
      onClick={() => user.enabled ? disable.mutate() : enable.mutate()}
      disabled={isPending}
      className="text-xs px-2 py-1 rounded disabled:opacity-60"
      style={{
        color: user.enabled ? 'var(--color-error)' : 'var(--color-success)',
        border: '1px solid var(--color-border)',
      }}
    >
      {isPending ? '…' : user.enabled ? 'Disable' : 'Enable'}
    </button>
  )
}

// ── Delete confirm (hook at top level) ────────────────────────────────────────

function DeleteUserRow({ user, onDone }: { user: PlatformUser; onDone: () => void }) {
  const del = useDeleteUser(user.id)
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
        {del.isPending ? 'Deleting…' : 'Delete User'}
      </button>
    </div>
  )
}

// ── Create user modal ─────────────────────────────────────────────────────────

function CreateUserModal({ onClose }: { onClose: () => void }) {
  const create  = useCreateUser()
  const { data: rolesData }   = useRoles()
  const { data: officesData } = useOffices()

  const roles   = (rolesData as { data?: { id: string; name: string }[] } | undefined)?.data ?? []
  const offices = (officesData as { data?: { id: string; name: string }[] } | undefined)?.data ?? []

  const [form, setForm] = useState<CreateUserRequest>({
    username: '', firstname: '', lastname: '',
    email: '', password: '', officeId: '', roleIds: [],
  })

  function set<K extends keyof CreateUserRequest>(k: K, v: CreateUserRequest[K]) {
    setForm(prev => ({ ...prev, [k]: v }))
  }

  function toggleRole(id: string) {
    setForm(prev => ({
      ...prev,
      roleIds: prev.roleIds.includes(id)
        ? prev.roleIds.filter(r => r !== id)
        : [...prev.roleIds, id],
    }))
  }

  async function save() {
    await create.mutateAsync(form)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.5)' }}>
      <div className="w-full max-w-lg rounded-2xl p-6 overflow-y-auto" style={{ background: 'var(--bg-card)', maxHeight: '90vh' }}>
        <h2 className="text-base font-semibold mb-4" style={{ color: 'var(--color-text)' }}>New User</h2>

        <div className="space-y-3">
          {([
            { label: 'First Name', key: 'firstname' as const, type: 'text' },
            { label: 'Last Name',  key: 'lastname'  as const, type: 'text' },
            { label: 'Username',   key: 'username'  as const, type: 'text' },
            { label: 'Email',      key: 'email'     as const, type: 'email' },
            { label: 'Password',   key: 'password'  as const, type: 'password' },
          ]).map(({ label, key, type }) => (
            <div key={key}>
              <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>{label}</label>
              <input
                type={type}
                value={form[key] as string}
                onChange={e => set(key, e.target.value)}
                className="w-full px-2 py-1.5 rounded text-sm outline-none"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
              />
            </div>
          ))}

          {/* Office */}
          <div>
            <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>Office</label>
            <select
              value={form.officeId}
              onChange={e => set('officeId', e.target.value)}
              className="w-full px-2 py-1.5 rounded text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
            >
              <option value="">Select office…</option>
              {offices.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
            </select>
          </div>

          {/* Roles */}
          <div>
            <label className="block text-xs mb-2 font-medium" style={{ color: 'var(--color-muted)' }}>Roles</label>
            <div className="flex flex-wrap gap-2">
              {roles.map(r => (
                <button
                  key={r.id}
                  onClick={() => toggleRole(r.id)}
                  className="text-xs px-3 py-1.5 rounded-full"
                  style={{
                    background: form.roleIds.includes(r.id) ? 'var(--color-primary)' : 'var(--bg-subtle)',
                    color:      form.roleIds.includes(r.id) ? '#fff' : 'var(--color-muted)',
                    border:     '1px solid var(--color-border)',
                  }}
                >
                  {r.name}
                </button>
              ))}
            </div>
          </div>
        </div>

        {create.isError && (
          <p className="text-xs mt-3" style={{ color: 'var(--color-error)' }}>Failed to create user.</p>
        )}

        <div className="flex justify-end gap-3 mt-5">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button onClick={save} disabled={create.isPending}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {create.isPending ? 'Creating…' : 'Create User'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function UsersPage() {
  const { data, isLoading } = useUsers()
  const users: PlatformUser[] = (data as { data?: PlatformUser[] } | undefined)?.data ?? []

  const [creating, setCreating] = useState(false)
  const [deleting, setDeleting] = useState<PlatformUser | null>(null)
  const [search,   setSearch]   = useState('')

  const filtered = users.filter(u =>
    !search || [u.username, u.firstname, u.lastname, u.email]
      .some(v => v.toLowerCase().includes(search.toLowerCase()))
  )

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Users"
        subtitle="Platform user accounts and role assignments"
        actions={
          <button onClick={() => setCreating(true)}
            className="text-xs px-3 py-1.5 rounded-lg text-white"
            style={{ background: 'var(--color-primary)' }}>
            + New User
          </button>
        }
      />

      {/* Search */}
      <div className="mb-4">
        <input
          type="text"
          placeholder="Search by name, username or email…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-full max-w-sm px-3 py-2 rounded-lg text-sm outline-none"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
        />
      </div>

      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
              {['Name', 'Username', 'Email', 'Office', 'Roles', 'Status', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                  style={{ color: 'var(--color-muted)' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 && (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                No users found.
              </td></tr>
            )}
            {filtered.map(u => (
              <tr key={u.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                <td className="px-4 py-3 font-medium text-sm" style={{ color: 'var(--color-text)' }}>
                  {u.firstname} {u.lastname}
                </td>
                <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>{u.username}</td>
                <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>{u.email}</td>
                <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>{u.officeName}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1">
                    {u.roles.map(r => (
                      <span key={r.id} className="text-xs px-2 py-0.5 rounded-full"
                        style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
                        {r.name}
                      </span>
                    ))}
                  </div>
                </td>
                <td className="px-4 py-3">
                  <StatusBadge label={u.enabled ? 'ACTIVE' : 'DISABLED'} variant={u.enabled ? 'success' : 'neutral'} />
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-1.5">
                    <ToggleUserRow user={u} />
                    <button onClick={() => setDeleting(u)}
                      className="text-xs px-2 py-1 rounded"
                      style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}>
                      Delete
                    </button>
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
            Delete <span className="font-mono">{deleting.username}</span>?
          </p>
          <p className="text-xs mt-1" style={{ color: 'var(--color-muted)' }}>This action cannot be undone.</p>
          <DeleteUserRow user={deleting} onDone={() => setDeleting(null)} />
        </div>
      )}

      {creating && <CreateUserModal onClose={() => setCreating(false)} />}
    </div>
  )
}
