// web-react/src/app/features/admin/RolesPage.tsx
import { useState, useMemo } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import {
  useRoles, useAllPermissions, useCreateRole, useUpdateRole,
  useUpdateRolePermissions, useDeleteRole,
} from './api/useAdmin'
import type { Role, Permission, CreateRoleRequest } from './api/types'

// ── Delete confirm (hook at top level) ────────────────────────────────────────

function DeleteRoleRow({ role, onDone }: { role: Role; onDone: () => void }) {
  const del = useDeleteRole(role.id)
  return (
    <div className="flex justify-end gap-3 mt-4">
      <button onClick={onDone} className="text-xs px-3 py-1.5 rounded-lg"
        style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
      <button
        onClick={async () => { await del.mutateAsync(); onDone() }}
        disabled={del.isPending}
        className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
        style={{ background: 'var(--color-error)' }}
      >
        {del.isPending ? 'Deleting…' : 'Delete Role'}
      </button>
    </div>
  )
}

// ── Permissions matrix modal ──────────────────────────────────────────────────

function PermissionsModal({ role, onClose }: { role: Role; onClose: () => void }) {
  const { data: permsData } = useAllPermissions()
  const update = useUpdateRolePermissions(role.id)

  const allPerms: Permission[] = (permsData as { data?: Permission[] } | undefined)?.data ?? []

  const [selected, setSelected] = useState<Set<string>>(
    () => new Set(role.permissions.map(p => p.id))
  )

  // Group permissions by grouping field
  const grouped = useMemo(() => {
    const map = new Map<string, Permission[]>()
    allPerms.forEach(p => {
      if (!map.has(p.grouping)) map.set(p.grouping, [])
      map.get(p.grouping)!.push(p)
    })
    return map
  }, [allPerms])

  function togglePerm(id: string) {
    setSelected(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  function toggleGroup(perms: Permission[]) {
    const ids = perms.map(p => p.id)
    const allChecked = ids.every(id => selected.has(id))
    setSelected(prev => {
      const next = new Set(prev)
      if (allChecked) ids.forEach(id => next.delete(id))
      else ids.forEach(id => next.add(id))
      return next
    })
  }

  async function save() {
    await update.mutateAsync({ permissionIds: Array.from(selected) })
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.5)' }}>
      <div className="w-full max-w-2xl rounded-2xl p-6 overflow-y-auto" style={{ background: 'var(--bg-card)', maxHeight: '90vh' }}>
        <h2 className="text-base font-semibold mb-1" style={{ color: 'var(--color-text)' }}>
          Permissions — {role.name}
        </h2>
        <p className="text-xs mb-4" style={{ color: 'var(--color-muted)' }}>
          {selected.size} of {allPerms.length} permissions selected
        </p>

        <div className="space-y-5">
          {Array.from(grouped.entries()).map(([group, perms]) => {
            const allChecked = perms.every(p => selected.has(p.id))
            return (
              <div key={group}>
                <div className="flex items-center gap-2 mb-2">
                  <input
                    type="checkbox"
                    checked={allChecked}
                    onChange={() => toggleGroup(perms)}
                    className="rounded"
                  />
                  <span className="text-xs font-semibold uppercase tracking-wider"
                    style={{ color: 'var(--color-text)' }}>
                    {group}
                  </span>
                  <span className="text-xs" style={{ color: 'var(--color-muted)' }}>
                    ({perms.filter(p => selected.has(p.id)).length}/{perms.length})
                  </span>
                </div>
                <div className="grid grid-cols-2 gap-1 pl-5">
                  {perms.map(p => (
                    <label key={p.id} className="flex items-center gap-2 cursor-pointer py-0.5">
                      <input
                        type="checkbox"
                        checked={selected.has(p.id)}
                        onChange={() => togglePerm(p.id)}
                        className="rounded"
                      />
                      <span className="text-xs" style={{ color: 'var(--color-text)' }}>
                        {p.actionName}
                        {p.canMakerChecker && (
                          <span className="ml-1 text-xs" style={{ color: 'var(--color-muted)' }}>(MC)</span>
                        )}
                      </span>
                    </label>
                  ))}
                </div>
              </div>
            )
          })}
        </div>

        {update.isError && (
          <p className="text-xs mt-3" style={{ color: 'var(--color-error)' }}>Failed to update permissions.</p>
        )}

        <div className="flex justify-end gap-3 mt-5">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button onClick={save} disabled={update.isPending}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {update.isPending ? 'Saving…' : 'Save Permissions'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Create / Edit role modal ───────────────────────────────────────────────────

function RoleModal({
  initial,
  onClose,
}: {
  initial?: Role
  onClose: () => void
}) {
  const isEdit = !!initial
  const create = useCreateRole()
  const update = useUpdateRole(initial?.id ?? '')

  const [form, setForm] = useState<CreateRoleRequest>({
    name:        initial?.name        ?? '',
    description: initial?.description ?? '',
  })

  async function save() {
    const payload = { name: form.name, description: form.description }
    if (isEdit) await update.mutateAsync(payload)
    else        await create.mutateAsync(payload)
    onClose()
  }

  const isPending = create.isPending || update.isPending
  const isError   = create.isError   || update.isError

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.5)' }}>
      <div className="w-full max-w-md rounded-2xl p-6" style={{ background: 'var(--bg-card)' }}>
        <h2 className="text-base font-semibold mb-4" style={{ color: 'var(--color-text)' }}>
          {isEdit ? 'Edit Role' : 'New Role'}
        </h2>

        <div className="space-y-3">
          {([
            { label: 'Role Name',   key: 'name'        as const },
            { label: 'Description', key: 'description' as const },
          ]).map(({ label, key }) => (
            <div key={key}>
              <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>{label}</label>
              <input
                type="text"
                value={form[key]}
                onChange={e => setForm(prev => ({ ...prev, [key]: e.target.value }))}
                className="w-full px-2 py-1.5 rounded text-sm outline-none"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
              />
            </div>
          ))}
        </div>

        {isError && (
          <p className="text-xs mt-3" style={{ color: 'var(--color-error)' }}>Failed to save role.</p>
        )}

        <div className="flex justify-end gap-3 mt-5">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button onClick={save} disabled={isPending}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {isPending ? 'Saving…' : isEdit ? 'Update Role' : 'Create Role'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function RolesPage() {
  const { data, isLoading } = useRoles()
  const roles: Role[] = (data as { data?: Role[] } | undefined)?.data ?? []

  const [creating,   setCreating]   = useState(false)
  const [editing,    setEditing]    = useState<Role | null>(null)
  const [deleting,   setDeleting]   = useState<Role | null>(null)
  const [viewPerms,  setViewPerms]  = useState<Role | null>(null)

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Roles & Permissions"
        subtitle="Platform roles and their permission assignments"
        actions={
          <button onClick={() => setCreating(true)}
            className="text-xs px-3 py-1.5 rounded-lg text-white"
            style={{ background: 'var(--color-primary)' }}>
            + New Role
          </button>
        }
      />

      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
              {['Name', 'Description', 'Permissions', 'Status', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                  style={{ color: 'var(--color-muted)' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {roles.length === 0 && (
              <tr><td colSpan={5} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                No roles defined.
              </td></tr>
            )}
            {roles.map(r => (
              <tr key={r.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                <td className="px-4 py-3 font-medium text-sm" style={{ color: 'var(--color-text)' }}>{r.name}</td>
                <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>{r.description}</td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => setViewPerms(r)}
                    className="text-xs px-2 py-1 rounded"
                    style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}
                  >
                    {r.permissions.length} permissions
                  </button>
                </td>
                <td className="px-4 py-3">
                  <StatusBadge label={r.disabled ? 'DISABLED' : 'ACTIVE'} variant={r.disabled ? 'neutral' : 'success'} />
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-1.5">
                    <button onClick={() => setEditing(r)}
                      className="text-xs px-2 py-1 rounded"
                      style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>Edit</button>
                    <button onClick={() => setDeleting(r)}
                      className="text-xs px-2 py-1 rounded"
                      style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}>Delete</button>
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
            Delete <span className="font-mono">{deleting.name}</span>?
          </p>
          <p className="text-xs mt-1" style={{ color: 'var(--color-muted)' }}>This action cannot be undone.</p>
          <DeleteRoleRow role={deleting} onDone={() => setDeleting(null)} />
        </div>
      )}

      {creating  && <RoleModal onClose={() => setCreating(false)} />}
      {editing   && <RoleModal initial={editing} onClose={() => setEditing(null)} />}
      {viewPerms && <PermissionsModal role={viewPerms} onClose={() => setViewPerms(null)} />}
    </div>
  )
}
