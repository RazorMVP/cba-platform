// web-react/src/app/features/admin/OfficesPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { useOffices, useCreateOffice, useUpdateOffice } from './api/useAdmin'
import type { Office, CreateOfficeRequest } from './api/types'

// ── Create / Edit modal ───────────────────────────────────────────────────────

function OfficeModal({
  initial,
  offices,
  onClose,
}: {
  initial?: Office
  offices: Office[]
  onClose: () => void
}) {
  const isEdit = !!initial
  const create = useCreateOffice()
  const update = useUpdateOffice(initial?.id ?? '')

  const [form, setForm] = useState<CreateOfficeRequest>({
    name:        initial?.name        ?? '',
    externalId:  initial?.externalId  ?? '',
    openingDate: initial?.openingDate ?? new Date().toISOString().slice(0, 10),
    parentId:    initial?.parentId    ?? '',
  })

  function set<K extends keyof CreateOfficeRequest>(k: K, v: CreateOfficeRequest[K]) {
    setForm(prev => ({ ...prev, [k]: v }))
  }

  async function save() {
    const payload = { ...form, parentId: form.parentId || undefined }
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
          {isEdit ? 'Edit Office' : 'New Office'}
        </h2>

        <div className="space-y-3">
          {([
            { label: 'Office Name', key: 'name'       as const },
            { label: 'External ID', key: 'externalId' as const },
          ]).map(({ label, key }) => (
            <div key={key}>
              <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>{label}</label>
              <input
                type="text"
                value={form[key] as string}
                onChange={e => set(key, e.target.value)}
                className="w-full px-2 py-1.5 rounded text-sm outline-none"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
              />
            </div>
          ))}

          <div>
            <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>Opening Date</label>
            <input
              type="date"
              value={form.openingDate}
              onChange={e => set('openingDate', e.target.value)}
              className="w-full px-2 py-1.5 rounded text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
            />
          </div>

          <div>
            <label className="block text-xs mb-1 font-medium" style={{ color: 'var(--color-muted)' }}>
              Parent Office (optional)
            </label>
            <select
              value={form.parentId ?? ''}
              onChange={e => set('parentId', e.target.value || undefined)}
              className="w-full px-2 py-1.5 rounded text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
            >
              <option value="">— None (root office) —</option>
              {offices
                .filter(o => o.id !== initial?.id)
                .map(o => <option key={o.id} value={o.id}>{o.name}</option>)
              }
            </select>
          </div>
        </div>

        {isError && (
          <p className="text-xs mt-3" style={{ color: 'var(--color-error)' }}>Failed to save office.</p>
        )}

        <div className="flex justify-end gap-3 mt-5">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button onClick={save} disabled={isPending}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {isPending ? 'Saving…' : isEdit ? 'Update Office' : 'Create Office'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function OfficesPage() {
  const { data, isLoading } = useOffices()
  const offices: Office[] = (data as { data?: Office[] } | undefined)?.data ?? []

  const [creating, setCreating] = useState(false)
  const [editing,  setEditing]  = useState<Office | null>(null)
  const [search,   setSearch]   = useState('')

  const filtered = offices.filter(o =>
    !search || o.name.toLowerCase().includes(search.toLowerCase())
  )

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Offices"
        subtitle="Branch office hierarchy"
        actions={
          <button onClick={() => setCreating(true)}
            className="text-xs px-3 py-1.5 rounded-lg text-white"
            style={{ background: 'var(--color-primary)' }}>
            + New Office
          </button>
        }
      />

      <div className="mb-4">
        <input
          type="text"
          placeholder="Search offices…"
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
              {['Name', 'External ID', 'Parent', 'Hierarchy', 'Opening Date', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                  style={{ color: 'var(--color-muted)' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 && (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                No offices found.
              </td></tr>
            )}
            {filtered.map(o => (
              <tr key={o.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                <td className="px-4 py-3 font-medium text-sm" style={{ color: 'var(--color-text)' }}>{o.name}</td>
                <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>
                  {o.externalId || '—'}
                </td>
                <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>
                  {o.parentName ?? '— Root —'}
                </td>
                <td className="px-4 py-3 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>
                  {o.hierarchy}
                </td>
                <td className="px-4 py-3 text-xs tabular-nums" style={{ color: 'var(--color-muted)' }}>
                  {o.openingDate ? new Date(o.openingDate).toLocaleDateString() : '—'}
                </td>
                <td className="px-4 py-3">
                  <button onClick={() => setEditing(o)}
                    className="text-xs px-2 py-1 rounded"
                    style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>
                    Edit
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {creating && <OfficeModal offices={offices} onClose={() => setCreating(false)} />}
      {editing  && <OfficeModal initial={editing} offices={offices} onClose={() => setEditing(null)} />}
    </div>
  )
}
