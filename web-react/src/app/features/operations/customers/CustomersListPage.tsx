// web-react/src/app/features/operations/customers/CustomersListPage.tsx
import { useState, useEffect, useRef } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { DataTable, type ColumnDef } from '@/shared/components/DataTable'
import { StatusBadge, type BadgeVariant } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useCustomers, useCreateCustomer } from '../api/useCustomers'
import type { Customer, KycStatus } from '../api/types'

const KYC_TABS: { label: string; value: string }[] = [
  { label: 'All', value: '' },
  { label: 'Pending KYC', value: 'PENDING_KYC' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Suspended', value: 'SUSPENDED' },
  { label: 'Closed', value: 'CLOSED' },
]

function kycVariant(status: KycStatus): BadgeVariant {
  const map: Partial<Record<KycStatus, BadgeVariant>> = {
    ACTIVE: 'success',
    PENDING_KYC: 'warning',
    SUSPENDED: 'error',
    REJECTED: 'error',
    CLOSED: 'neutral',
    WITHDRAWN: 'neutral',
    TRANSFER_IN_PROGRESS: 'info',
  }
  return map[status] ?? 'neutral'
}

const PAGE_SIZE = 20

const columns: ColumnDef<Customer>[] = [
  {
    key: 'name',
    header: 'Customer',
    cell: row => (
      <Link to={`/customers/${row.id}`} className="font-medium hover:underline" style={{ color: 'var(--color-info)' }}>
        {row.firstName} {row.lastName}
      </Link>
    ),
  },
  { key: 'email', header: 'Email', cell: row => row.email },
  { key: 'phone', header: 'Phone', cell: row => row.phone },
  { key: 'office', header: 'Office', cell: row => row.officeName ?? '—' },
  {
    key: 'status',
    header: 'KYC Status',
    cell: row => (
      <StatusBadge label={row.kycStatus.replace(/_/g, ' ')} variant={kycVariant(row.kycStatus)} />
    ),
  },
  {
    key: 'created',
    header: 'Joined',
    numeric: true,
    cell: row => new Date(row.createdAt).toLocaleDateString(),
  },
]

export default function CustomersListPage() {
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [activeTab, setActiveTab] = useState('')
  const [page, setPage] = useState(0)
  const [showCreate, setShowCreate] = useState(false)

  // Debounce search input — 250ms
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => { setDebouncedSearch(search); setPage(0) }, 250)
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current) }
  }, [search])

  const { data, isLoading } = useCustomers({
    page,
    size: PAGE_SIZE,
    search: debouncedSearch || undefined,
    kycStatus: activeTab || undefined,
  })

  const customers = data?.data ?? []
  const total = data?.meta?.total ?? 0
  const totalPages = Math.ceil(total / PAGE_SIZE)

  return (
    <div>
      <PageHeader
        title="Customers"
        subtitle={`${total.toLocaleString()} total`}
        actions={
          <button
            onClick={() => setShowCreate(true)}
            className="px-4 py-2 rounded-lg text-sm font-medium text-white"
            style={{ background: 'var(--color-primary)' }}
          >
            + New Customer
          </button>
        }
      />

      {/* Filters */}
      <div className="flex items-center gap-4 mb-6">
        <input
          type="search"
          placeholder="Search by name, email, phone…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="flex-1 max-w-xs px-3 py-2 rounded-lg text-sm outline-none"
          style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--color-border)',
            color: 'var(--color-text)',
          }}
        />

        {/* KYC status tabs */}
        <div className="flex gap-1 rounded-lg p-1" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          {KYC_TABS.map(tab => (
            <button
              key={tab.value}
              onClick={() => { setActiveTab(tab.value); setPage(0) }}
              className="px-3 py-1 rounded-md text-xs font-medium transition-colors"
              style={{
                background: activeTab === tab.value ? 'var(--color-primary)' : 'transparent',
                color: activeTab === tab.value ? '#fff' : 'var(--color-muted)',
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Table */}
      <div className="rounded-xl" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <DataTable
          columns={columns}
          data={customers}
          loading={isLoading}
          emptyMessage="No customers match your filters"
          getRowKey={r => r.id}
        />

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-6 py-3" style={{ borderTop: '1px solid var(--color-border)' }}>
            <span className="text-xs tabular-nums" style={{ color: 'var(--color-muted)' }}>
              Page {page + 1} of {totalPages} · {total.toLocaleString()} results
            </span>
            <div className="flex gap-2">
              <button
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
                className="px-3 py-1 text-xs rounded-md disabled:opacity-40"
                style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
              >
                ← Prev
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage(p => p + 1)}
                className="px-3 py-1 text-xs rounded-md disabled:opacity-40"
                style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
              >
                Next →
              </button>
            </div>
          </div>
        )}
      </div>

      <CreateCustomerModal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onCreated={id => { setShowCreate(false); navigate(`/customers/${id}`) }}
      />
    </div>
  )
}

// ── Create Customer Modal ────────────────────────────────────────

interface CreateForm {
  firstName: string
  lastName: string
  email: string
  phone: string
  dateOfBirth: string
  nationalId: string
}

function CreateCustomerModal({ open, onClose, onCreated }: {
  open: boolean
  onClose: () => void
  onCreated: (id: string) => void
}) {
  const create = useCreateCustomer()
  const [form, setForm] = useState<CreateForm>({
    firstName: '', lastName: '', email: '', phone: '', dateOfBirth: '', nationalId: '',
  })
  const [error, setError] = useState('')

  const set = (k: keyof CreateForm) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value }))

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    try {
      const res = await create.mutateAsync(form)
      const id = (res.data as { data: { id: string } }).data?.id
      if (id) onCreated(id)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create customer')
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New Customer" size="md"
      footer={
        <div className="flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button form="create-customer-form" type="submit" disabled={create.isPending}
            className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {create.isPending ? 'Creating…' : 'Create Customer'}
          </button>
        </div>
      }
    >
      <form id="create-customer-form" onSubmit={handleSubmit} className="space-y-4 p-6">
        {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
        <div className="grid grid-cols-2 gap-4">
          <Field label="First Name" value={form.firstName} onChange={set('firstName')} required />
          <Field label="Last Name" value={form.lastName} onChange={set('lastName')} required />
        </div>
        <Field label="Email" type="email" value={form.email} onChange={set('email')} required />
        <Field label="Phone" type="tel" value={form.phone} onChange={set('phone')} required />
        <Field label="Date of Birth" type="date" value={form.dateOfBirth} onChange={set('dateOfBirth')} />
        <Field label="National ID" value={form.nationalId} onChange={set('nationalId')} />
      </form>
    </Modal>
  )
}

function Field({ label, ...props }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <input
        {...props}
        className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
      />
    </div>
  )
}
