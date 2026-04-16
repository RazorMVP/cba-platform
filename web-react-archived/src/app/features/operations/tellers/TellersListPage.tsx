// web-react/src/app/features/operations/tellers/TellersListPage.tsx
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { DataTable, type ColumnDef } from '@/shared/components/DataTable'
import { StatusBadge, type BadgeVariant } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useTellers, useCreateTeller } from '../api/useTellers'
import type { Teller, TellerStatus } from '../api/types'

const STATUS_TABS = [
  { label: 'All', value: '' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Inactive', value: 'INACTIVE' },
  { label: 'Closed', value: 'CLOSED' },
]

function tellerVariant(s: TellerStatus): BadgeVariant {
  const m: Record<TellerStatus, BadgeVariant> = { ACTIVE: 'success', INACTIVE: 'warning', CLOSED: 'neutral' }
  return m[s]
}

const columns: ColumnDef<Teller>[] = [
  { key: 'name', header: 'Name', cell: r => <Link to={`/tellers/${r.id}`} className="font-medium hover:underline" style={{ color: 'var(--color-info)' }}>{r.name}</Link> },
  { key: 'office', header: 'Office', cell: r => r.officeName },
  { key: 'desc', header: 'Description', cell: r => r.description ?? '—' },
  { key: 'status', header: 'Status', cell: r => <StatusBadge label={r.status} variant={tellerVariant(r.status)} /> },
  { key: 'created', header: 'Created', numeric: true, cell: r => new Date(r.createdAt).toLocaleDateString() },
]

export default function TellersListPage() {
  const navigate = useNavigate()
  const [activeStatus, setActiveStatus] = useState('')
  const [search, setSearch] = useState('')
  const [showCreate, setShowCreate] = useState(false)

  const { data, isLoading } = useTellers({ status: activeStatus || undefined })
  const tellers = (data?.data ?? []).filter(t =>
    !search || t.name.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div>
      <PageHeader
        title="Tellers"
        subtitle={`${tellers.length} teller desks`}
        actions={
          <button onClick={() => setShowCreate(true)}
            className="px-4 py-2 rounded-lg text-sm font-medium text-white"
            style={{ background: 'var(--color-primary)' }}>
            + New Teller
          </button>
        }
      />

      <div className="flex items-center gap-4 mb-6">
        <input type="search" placeholder="Search tellers…" value={search} onChange={e => setSearch(e.target.value)}
          className="px-3 py-2 rounded-lg text-sm outline-none max-w-xs"
          style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
        <div className="flex gap-1 rounded-lg p-1" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          {STATUS_TABS.map(t => (
            <button key={t.value} onClick={() => setActiveStatus(t.value)}
              className="px-3 py-1 rounded-md text-xs font-medium transition-colors"
              style={{ background: activeStatus === t.value ? 'var(--color-primary)' : 'transparent', color: activeStatus === t.value ? '#fff' : 'var(--color-muted)' }}>
              {t.label}
            </button>
          ))}
        </div>
      </div>

      <div className="rounded-xl" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <DataTable columns={columns} data={tellers} loading={isLoading}
          emptyMessage="No tellers found" getRowKey={r => r.id} />
      </div>

      <CreateTellerModal open={showCreate} onClose={() => setShowCreate(false)}
        onCreated={id => { setShowCreate(false); navigate(`/tellers/${id}`) }} />
    </div>
  )
}

function CreateTellerModal({ open, onClose, onCreated }: {
  open: boolean; onClose: () => void; onCreated: (id: string) => void
}) {
  const create = useCreateTeller()
  const [form, setForm] = useState({ name: '', officeId: '', description: '' })
  const [error, setError] = useState('')

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault(); setError('')
    try {
      const res = await create.mutateAsync(form)
      const id = (res.data as { data: { id: string } }).data?.id
      if (id) onCreated(id)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to create teller') }
  }

  return (
    <Modal open={open} onClose={onClose} title="New Teller Desk" size="sm"
      footer={
        <div className="flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button form="create-teller-form" type="submit" disabled={create.isPending}
            className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
            {create.isPending ? 'Creating…' : 'Create Teller'}
          </button>
        </div>
      }>
      <form id="create-teller-form" onSubmit={handleSubmit} className="space-y-4 p-6">
        {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
        <Field label="Teller Name" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} required />
        <Field label="Office ID" value={form.officeId} onChange={e => setForm(f => ({ ...f, officeId: e.target.value }))} required />
        <Field label="Description (optional)" value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
      </form>
    </Modal>
  )
}

function Field({ label, ...props }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <input {...props} className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
    </div>
  )
}
