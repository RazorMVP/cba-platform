// web-react/src/app/features/operations/customers/CustomerDetailPage.tsx
import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge, type BadgeVariant } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { DataTable, type ColumnDef } from '@/shared/components/DataTable'
import {
  useCustomer,
  useUpdateCustomer,
  useCustomerCommand,
  useCreateCustomer,
} from '../api/useCustomers'
import { useAccounts } from '../api/useAccounts'
import { useLoans } from '../api/useLoans'
import type { Account, Loan, KycStatus } from '../api/types'

type Tab = 'overview' | 'accounts' | 'loans' | 'staff' | 'transfer'

function kycVariant(s: KycStatus): BadgeVariant {
  const m: Partial<Record<KycStatus, BadgeVariant>> = {
    ACTIVE: 'success', PENDING_KYC: 'warning', SUSPENDED: 'error',
    REJECTED: 'error', CLOSED: 'neutral', WITHDRAWN: 'neutral', TRANSFER_IN_PROGRESS: 'info',
  }
  return m[s] ?? 'neutral'
}

const accountCols: ColumnDef<Account>[] = [
  { key: 'num', header: 'Account #', cell: r => <Link to={`/accounts/${r.id}`} className="hover:underline" style={{ color: 'var(--color-info)' }}>{r.accountNumber}</Link> },
  { key: 'type', header: 'Type', cell: r => r.accountType },
  { key: 'bal', header: 'Balance', numeric: true, cell: r => `${r.balance.toLocaleString()} ${r.currencyCode}` },
  { key: 'status', header: 'Status', cell: r => <StatusBadge label={r.status} variant={r.status === 'ACTIVE' ? 'success' : r.status === 'FROZEN' ? 'error' : 'neutral'} /> },
]

const loanCols: ColumnDef<Loan>[] = [
  { key: 'num', header: 'Loan #', cell: r => <Link to={`/loans/${r.id}`} className="hover:underline" style={{ color: 'var(--color-info)' }}>{r.loanAccountNumber}</Link> },
  { key: 'product', header: 'Product', cell: r => r.productName },
  { key: 'outstanding', header: 'Outstanding', numeric: true, cell: r => `${r.outstandingBalance.toLocaleString()} ${r.currencyCode}` },
  { key: 'status', header: 'Status', cell: r => <StatusBadge label={r.status.replace(/_/g, ' ')} variant={r.status === 'ACTIVE' ? 'success' : r.status === 'IN_ARREARS' ? 'error' : r.status === 'DISBURSED' ? 'info' : 'warning'} /> },
]

export default function CustomerDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isNew = id === 'new'
  const [tab, setTab] = useState<Tab>('overview')
  const [modal, setModal] = useState<string | null>(null)

  const { data: customer, isLoading } = useCustomer(id ?? '')
  const { data: accountsData } = useAccounts({ page: 0, size: 20 })
  const { data: loansData } = useLoans({ page: 0, size: 20 })

  const updateCustomer = useUpdateCustomer(id ?? '')
  const command = useCustomerCommand(id ?? '')
  const createCustomer = useCreateCustomer()

  // Edit form state
  const [editMode, setEditMode] = useState(isNew)
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', phone: '' })
  const [formError, setFormError] = useState('')

  function enterEditMode() {
    if (customer) setForm({ firstName: customer.firstName, lastName: customer.lastName, email: customer.email, phone: customer.phone })
    setEditMode(true)
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault()
    setFormError('')
    try {
      if (isNew) {
        const res = await createCustomer.mutateAsync(form)
        const newId = (res.data as { data: { id: string } }).data?.id
        if (newId) navigate(`/customers/${newId}`)
      } else {
        await updateCustomer.mutateAsync(form)
        setEditMode(false)
      }
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Save failed')
    }
  }

  async function handleCommand(cmd: string, body?: Record<string, unknown>) {
    try {
      await command.mutateAsync({ command: cmd, body })
      setModal(null)
    } catch (_) { /* surface in modal */ }
  }

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  if (isNew || !customer) {
    return (
      <div>
        <PageHeader title="New Customer" actions={<Link to="/customers" className="text-sm" style={{ color: 'var(--color-muted)' }}>← Back</Link>} />
        <div className="rounded-xl p-6 max-w-lg" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <form onSubmit={handleSave} className="space-y-4">
            {formError && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{formError}</p>}
            <div className="grid grid-cols-2 gap-4">
              <Field label="First Name" value={form.firstName} onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))} required />
              <Field label="Last Name" value={form.lastName} onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))} required />
            </div>
            <Field label="Email" type="email" value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} required />
            <Field label="Phone" value={form.phone} onChange={e => setForm(f => ({ ...f, phone: e.target.value }))} required />
            <div className="flex justify-end gap-3 pt-2">
              <button type="button" onClick={() => navigate('/customers')} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
              <button type="submit" disabled={createCustomer.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
                {createCustomer.isPending ? 'Creating…' : 'Create Customer'}
              </button>
            </div>
          </form>
        </div>
      </div>
    )
  }

  const tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: 'Overview' },
    { key: 'accounts', label: 'Accounts' },
    { key: 'loans', label: 'Loans' },
    { key: 'staff', label: 'Staff' },
    { key: 'transfer', label: 'Transfer' },
  ]

  // Filter accounts/loans for this customer
  const myAccounts = (accountsData?.data ?? []).filter(a => a.customerId === id)
  const myLoans = (loansData?.data ?? []).filter(l => l.customerId === id)

  return (
    <div>
      <PageHeader
        title={`${customer.firstName} ${customer.lastName}`}
        subtitle={customer.email}
        actions={
          <div className="flex items-center gap-3">
            <StatusBadge label={customer.kycStatus.replace(/_/g, ' ')} variant={kycVariant(customer.kycStatus)} />
            <Link to="/customers" className="text-sm" style={{ color: 'var(--color-muted)' }}>← Back</Link>
          </div>
        }
      />

      {/* Command buttons */}
      <div className="flex flex-wrap gap-2 mb-6">
        {customer.kycStatus === 'PENDING_KYC' && (
          <CmdBtn onClick={() => setModal('reject')} danger>Reject</CmdBtn>
        )}
        {['ACTIVE', 'PENDING_KYC'].includes(customer.kycStatus) && (
          <CmdBtn onClick={() => setModal('withdraw')}>Withdraw</CmdBtn>
        )}
        {['SUSPENDED', 'PENDING_KYC'].includes(customer.kycStatus) && (
          <CmdBtn onClick={() => handleCommand('reactivate')}>Reactivate</CmdBtn>
        )}
        {customer.kycStatus === 'REJECTED' && (
          <CmdBtn onClick={() => handleCommand('undoRejection')}>Undo Rejection</CmdBtn>
        )}
        {customer.kycStatus === 'WITHDRAWN' && (
          <CmdBtn onClick={() => handleCommand('undoWithdrawal')}>Undo Withdrawal</CmdBtn>
        )}
        {customer.kycStatus === 'ACTIVE' && (
          <CmdBtn onClick={() => setModal('close')} danger>Close</CmdBtn>
        )}
        {!editMode && (
          <CmdBtn onClick={enterEditMode}>Edit Profile</CmdBtn>
        )}
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-6 rounded-lg p-1 w-fit" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        {tabs.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className="px-4 py-1.5 rounded-md text-sm font-medium transition-colors"
            style={{ background: tab === t.key ? 'var(--color-primary)' : 'transparent', color: tab === t.key ? '#fff' : 'var(--color-muted)' }}>
            {t.label}
          </button>
        ))}
      </div>

      {/* Tab: Overview */}
      {tab === 'overview' && (
        <div className="rounded-xl p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          {editMode ? (
            <form onSubmit={handleSave} className="space-y-4 max-w-lg">
              {formError && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{formError}</p>}
              <div className="grid grid-cols-2 gap-4">
                <Field label="First Name" value={form.firstName} onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))} required />
                <Field label="Last Name" value={form.lastName} onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))} required />
              </div>
              <Field label="Email" type="email" value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} required />
              <Field label="Phone" value={form.phone} onChange={e => setForm(f => ({ ...f, phone: e.target.value }))} required />
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={() => setEditMode(false)} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
                <button type="submit" disabled={updateCustomer.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
                  {updateCustomer.isPending ? 'Saving…' : 'Save Changes'}
                </button>
              </div>
            </form>
          ) : (
            <dl className="grid grid-cols-2 gap-x-8 gap-y-4">
              <InfoRow label="First Name" value={customer.firstName} />
              <InfoRow label="Last Name" value={customer.lastName} />
              <InfoRow label="Email" value={customer.email} />
              <InfoRow label="Phone" value={customer.phone} />
              <InfoRow label="KYC Status" value={customer.kycStatus.replace(/_/g, ' ')} />
              <InfoRow label="Office" value={customer.officeName ?? '—'} />
              <InfoRow label="External ID" value={customer.externalId ?? '—'} />
              <InfoRow label="Joined" value={new Date(customer.createdAt).toLocaleDateString()} />
            </dl>
          )}
        </div>
      )}

      {/* Tab: Accounts */}
      {tab === 'accounts' && (
        <div className="rounded-xl" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <div className="flex items-center justify-between px-6 py-4" style={{ borderBottom: '1px solid var(--color-border)' }}>
            <h2 className="font-display font-semibold text-sm" style={{ color: 'var(--color-text)' }}>Accounts</h2>
            <Link to={`/accounts/new?customerId=${id}`} className="text-xs font-medium px-3 py-1 rounded-lg text-white" style={{ background: 'var(--color-primary)' }}>+ Open Account</Link>
          </div>
          <DataTable columns={accountCols} data={myAccounts} emptyMessage="No accounts" getRowKey={r => r.id} />
        </div>
      )}

      {/* Tab: Loans */}
      {tab === 'loans' && (
        <div className="rounded-xl" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <div className="flex items-center justify-between px-6 py-4" style={{ borderBottom: '1px solid var(--color-border)' }}>
            <h2 className="font-display font-semibold text-sm" style={{ color: 'var(--color-text)' }}>Loans</h2>
            <Link to={`/loans/new?customerId=${id}`} className="text-xs font-medium px-3 py-1 rounded-lg text-white" style={{ background: 'var(--color-primary)' }}>+ Apply for Loan</Link>
          </div>
          <DataTable columns={loanCols} data={myLoans} emptyMessage="No loans" getRowKey={r => r.id} />
        </div>
      )}

      {/* Tab: Staff */}
      {tab === 'staff' && (
        <div className="rounded-xl p-6 space-y-4" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <InfoRow label="Assigned Staff" value={customer.staffName ?? 'None assigned'} />
          <div className="flex gap-3">
            <CmdBtn onClick={() => setModal('assignStaff')}>Assign Staff</CmdBtn>
            {customer.staffId && <CmdBtn onClick={() => handleCommand('unassignStaff')} danger>Unassign Staff</CmdBtn>}
          </div>
        </div>
      )}

      {/* Tab: Transfer */}
      {tab === 'transfer' && (
        <div className="rounded-xl p-6 space-y-4" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <p className="text-sm" style={{ color: 'var(--color-muted)' }}>Inter-branch transfer management for this customer.</p>
          <div className="flex flex-wrap gap-3">
            <CmdBtn onClick={() => setModal('proposeTransfer')}>Propose Transfer</CmdBtn>
            {customer.kycStatus === 'TRANSFER_IN_PROGRESS' && (
              <>
                <CmdBtn onClick={() => handleCommand('acceptTransfer')}>Accept Transfer</CmdBtn>
                <CmdBtn onClick={() => setModal('rejectTransfer')} danger>Reject Transfer</CmdBtn>
                <CmdBtn onClick={() => handleCommand('withdrawTransfer')}>Withdraw Transfer</CmdBtn>
              </>
            )}
            <CmdBtn onClick={() => setModal('directTransfer')}>Direct Transfer</CmdBtn>
          </div>
        </div>
      )}

      {/* Modals */}
      <ConfirmModal open={modal === 'reject'} title="Reject Customer" confirmLabel="Reject" danger
        onClose={() => setModal(null)} onConfirm={() => handleCommand('reject')}
        message="Are you sure you want to reject this customer? This will change their status to REJECTED." />
      <ConfirmModal open={modal === 'withdraw'} title="Withdraw Customer" confirmLabel="Withdraw"
        onClose={() => setModal(null)} onConfirm={() => handleCommand('withdraw')}
        message="Withdraw this customer from the onboarding process?" />
      <ConfirmModal open={modal === 'close'} title="Close Customer" confirmLabel="Close Account" danger
        onClose={() => setModal(null)} onConfirm={() => handleCommand('close')}
        message="Are you sure you want to permanently close this customer account?" />
      <InputModal open={modal === 'assignStaff'} title="Assign Staff" label="Staff ID" confirmLabel="Assign"
        onClose={() => setModal(null)} onConfirm={val => handleCommand('assignStaff', { staffId: val })} />
      <InputModal open={modal === 'proposeTransfer'} title="Propose Transfer" label="Destination Office ID" confirmLabel="Propose"
        onClose={() => setModal(null)} onConfirm={val => handleCommand('proposeTransfer', { destinationOfficeId: val })} />
      <InputModal open={modal === 'directTransfer'} title="Direct Transfer" label="Destination Office ID" confirmLabel="Transfer"
        onClose={() => setModal(null)} onConfirm={val => handleCommand('directTransfer', { destinationOfficeId: val })} />
    </div>
  )
}

// ── Shared sub-components ────────────────────────────────────────

function CmdBtn({ children, onClick, danger }: { children: React.ReactNode; onClick: () => void; danger?: boolean }) {
  return (
    <button onClick={onClick} className="px-3 py-1.5 text-xs font-medium rounded-lg transition-colors"
      style={{
        border: `1px solid ${danger ? 'var(--color-error)' : 'var(--color-border)'}`,
        color: danger ? 'var(--color-error)' : 'var(--color-text)',
      }}>
      {children}
    </button>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium mb-0.5" style={{ color: 'var(--color-muted)' }}>{label}</dt>
      <dd className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>{value}</dd>
    </div>
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

function ConfirmModal({ open, title, message, confirmLabel, danger, onClose, onConfirm }: {
  open: boolean; title: string; message: string; confirmLabel: string; danger?: boolean
  onClose: () => void; onConfirm: () => void
}) {
  return (
    <Modal open={open} onClose={onClose} title={title} size="sm"
      footer={
        <div className="flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button onClick={onConfirm} className="px-4 py-2 text-sm rounded-lg text-white"
            style={{ background: danger ? 'var(--color-error)' : 'var(--color-primary)' }}>{confirmLabel}</button>
        </div>
      }>
      <p className="p-6 text-sm" style={{ color: 'var(--color-text)' }}>{message}</p>
    </Modal>
  )
}

function InputModal({ open, title, label, confirmLabel, onClose, onConfirm }: {
  open: boolean; title: string; label: string; confirmLabel: string
  onClose: () => void; onConfirm: (val: string) => void
}) {
  const [val, setVal] = useState('')
  return (
    <Modal open={open} onClose={onClose} title={title} size="sm"
      footer={
        <div className="flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button onClick={() => { onConfirm(val); setVal('') }} className="px-4 py-2 text-sm rounded-lg text-white" style={{ background: 'var(--color-primary)' }}>{confirmLabel}</button>
        </div>
      }>
      <div className="p-6">
        <Field label={label} value={val} onChange={e => setVal(e.target.value)} />
      </div>
    </Modal>
  )
}
