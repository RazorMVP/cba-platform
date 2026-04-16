// web-react/src/app/features/operations/loans/LoanDetailPage.tsx
import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge, type BadgeVariant } from '@/shared/components/StatusBadge'
import { DataTable, type ColumnDef } from '@/shared/components/DataTable'
import { Modal } from '@/shared/components/Modal'
import { useLoan, useLoanSchedule, useLoanCommand, useCreateLoan } from '../api/useLoans'
import type { RepaymentScheduleItem, LoanStatus } from '../api/types'

type Tab = 'overview' | 'schedule' | 'charges' | 'guarantors' | 'documents'

function loanVariant(s: LoanStatus): BadgeVariant {
  const m: Partial<Record<LoanStatus, BadgeVariant>> = {
    ACTIVE: 'success', CLOSED_OBLIGATIONS_MET: 'success',
    IN_ARREARS: 'error', WRITTEN_OFF: 'error', REJECTED: 'error',
    DISBURSED: 'info', APPROVED: 'info',
    SUBMITTED: 'warning', UNDER_REVIEW: 'warning',
  }
  return m[s] ?? 'neutral'
}

function scheduleVariant(s: RepaymentScheduleItem['status']): BadgeVariant {
  const m: Record<typeof s, BadgeVariant> = { PAID: 'success', PARTIAL: 'warning', OVERDUE: 'error', PENDING: 'neutral' }
  return m[s]
}

const scheduleCols: ColumnDef<RepaymentScheduleItem>[] = [
  { key: 'due', header: 'Due Date', numeric: true, cell: r => new Date(r.dueDate).toLocaleDateString() },
  { key: 'principal', header: 'Principal Due', numeric: true, cell: r => r.principalDue.toLocaleString(undefined, { minimumFractionDigits: 2 }) },
  { key: 'interest', header: 'Interest Due', numeric: true, cell: r => r.interestDue.toLocaleString(undefined, { minimumFractionDigits: 2 }) },
  { key: 'total', header: 'Total Due', numeric: true, cell: r => <strong className="tabular-nums">{r.totalDue.toLocaleString(undefined, { minimumFractionDigits: 2 })}</strong> },
  { key: 'paid', header: 'Paid', numeric: true, cell: r => (r.principalPaid + r.interestPaid).toLocaleString(undefined, { minimumFractionDigits: 2 }) },
  { key: 'status', header: 'Status', cell: r => <StatusBadge label={r.status} variant={scheduleVariant(r.status)} /> },
]

export default function LoanDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isNew = id === 'new'
  const [tab, setTab] = useState<Tab>('overview')
  const [modal, setModal] = useState<string | null>(null)
  const [repayAmount, setRepayAmount] = useState('')
  const [error, setError] = useState('')

  const { data: loan, isLoading } = useLoan(id ?? '')
  const { data: scheduleData } = useLoanSchedule(id ?? '')
  const command = useLoanCommand(id ?? '')
  const createLoan = useCreateLoan()

  // New loan form
  const [newForm, setNewForm] = useState({
    customerId: '', productId: '', principalAmount: '', termMonths: '',
    interestRate: '', disbursementDate: '',
  })

  async function handleCommand(cmd: string, body?: Record<string, unknown>) {
    setError('')
    try {
      await command.mutateAsync({ command: cmd, body })
      setModal(null); setRepayAmount('')
    } catch (err) {
      setError(err instanceof Error ? err.message : `${cmd} failed`)
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault(); setError('')
    try {
      const res = await createLoan.mutateAsync({
        ...newForm,
        principalAmount: parseFloat(newForm.principalAmount) as unknown as number,
        termMonths: parseInt(newForm.termMonths) as unknown as number,
        interestRate: parseFloat(newForm.interestRate) as unknown as number,
      } as Parameters<typeof createLoan.mutateAsync>[0])
      const newId = (res.data as { data: { id: string } }).data?.id
      if (newId) navigate(`/loans/${newId}`)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to create loan application') }
  }

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  // New loan application form
  if (isNew) {
    return (
      <div>
        <PageHeader title="New Loan Application" actions={<Link to="/loans" className="text-sm" style={{ color: 'var(--color-muted)' }}>← Back</Link>} />
        <form onSubmit={handleCreate} className="rounded-xl p-6 max-w-lg space-y-4" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          {error && <ErrBox msg={error} />}
          <Field label="Customer ID" value={newForm.customerId} onChange={e => setNewForm(f => ({ ...f, customerId: e.target.value }))} required />
          <Field label="Product ID" value={newForm.productId} onChange={e => setNewForm(f => ({ ...f, productId: e.target.value }))} required />
          <div className="grid grid-cols-2 gap-4">
            <Field label="Principal Amount" type="number" min="0" value={newForm.principalAmount} onChange={e => setNewForm(f => ({ ...f, principalAmount: e.target.value }))} required />
            <Field label="Term (months)" type="number" min="1" value={newForm.termMonths} onChange={e => setNewForm(f => ({ ...f, termMonths: e.target.value }))} required />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Interest Rate (%)" type="number" step="0.01" value={newForm.interestRate} onChange={e => setNewForm(f => ({ ...f, interestRate: e.target.value }))} />
            <Field label="Disbursement Date" type="date" value={newForm.disbursementDate} onChange={e => setNewForm(f => ({ ...f, disbursementDate: e.target.value }))} />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={() => navigate('/loans')} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button type="submit" disabled={createLoan.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
              {createLoan.isPending ? 'Submitting…' : 'Submit Application'}
            </button>
          </div>
        </form>
      </div>
    )
  }

  if (!loan) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loan not found.</div>

  const schedule = scheduleData?.data ?? []

  const tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: 'Overview' },
    { key: 'schedule', label: `Schedule (${schedule.length})` },
    { key: 'charges', label: 'Charges' },
    { key: 'guarantors', label: 'Guarantors' },
    { key: 'documents', label: 'Documents' },
  ]

  return (
    <div>
      <PageHeader
        title={loan.loanAccountNumber}
        subtitle={`${loan.customerName} · ${loan.productName}`}
        actions={
          <div className="flex items-center gap-3">
            <StatusBadge label={loan.status.replace(/_/g, ' ')} variant={loanVariant(loan.status)} />
            <Link to="/loans" className="text-sm" style={{ color: 'var(--color-muted)' }}>← Back</Link>
          </div>
        }
      />

      {/* Outstanding balance hero */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        {[
          { label: 'Principal Amount', value: loan.principalAmount },
          { label: 'Outstanding Balance', value: loan.outstandingBalance },
          { label: 'Interest Rate', value: null, text: `${loan.interestRate}% p.a.` },
        ].map(item => (
          <div key={item.label} className="rounded-xl p-5" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
            <p className="text-xs font-semibold uppercase tracking-wider mb-1" style={{ color: 'var(--color-muted)' }}>{item.label}</p>
            <p className="font-display text-xl font-bold tabular-nums" style={{ color: 'var(--color-text)' }}>
              {item.text ?? `${item.value!.toLocaleString()} ${loan.currencyCode}`}
            </p>
          </div>
        ))}
      </div>

      {/* Action buttons */}
      <div className="flex flex-wrap gap-2 mb-6">
        {loan.status === 'SUBMITTED' && (
          <>
            <ActionBtn onClick={() => handleCommand('approve')}>Approve</ActionBtn>
            <ActionBtn onClick={() => setModal('reject')} danger>Reject</ActionBtn>
          </>
        )}
        {loan.status === 'APPROVED' && (
          <ActionBtn onClick={() => handleCommand('disburse')}>Disburse</ActionBtn>
        )}
        {['ACTIVE', 'IN_ARREARS', 'DISBURSED'].includes(loan.status) && (
          <ActionBtn onClick={() => setModal('repay')}>Make Repayment</ActionBtn>
        )}
      </div>

      {/* Tabs */}
      <div className="flex flex-wrap gap-1 mb-6 rounded-lg p-1 w-fit" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        {tabs.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className="px-4 py-1.5 rounded-md text-sm font-medium transition-colors"
            style={{ background: tab === t.key ? 'var(--color-primary)' : 'transparent', color: tab === t.key ? '#fff' : 'var(--color-muted)' }}>
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'overview' && (
        <div className="rounded-xl p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <dl className="grid grid-cols-2 gap-x-8 gap-y-4">
            <InfoRow label="Loan Number" value={loan.loanAccountNumber} />
            <InfoRow label="Customer" value={loan.customerName} />
            <InfoRow label="Product" value={loan.productName} />
            <InfoRow label="Term" value={`${loan.termMonths} months`} />
            <InfoRow label="Interest Rate" value={`${loan.interestRate}% p.a.`} />
            <InfoRow label="Currency" value={loan.currencyCode} />
            <InfoRow label="Disbursement Date" value={loan.disbursementDate ? new Date(loan.disbursementDate).toLocaleDateString() : '—'} />
            <InfoRow label="Maturity Date" value={loan.maturityDate ? new Date(loan.maturityDate).toLocaleDateString() : '—'} />
            <InfoRow label="Status" value={loan.status.replace(/_/g, ' ')} />
            <InfoRow label="Applied On" value={new Date(loan.createdAt).toLocaleDateString()} />
          </dl>
        </div>
      )}

      {tab === 'schedule' && (
        <div className="rounded-xl" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <DataTable columns={scheduleCols} data={schedule} emptyMessage="No schedule generated yet" getRowKey={r => r.id} />
        </div>
      )}

      {(tab === 'charges' || tab === 'guarantors' || tab === 'documents') && (
        <div className="rounded-xl p-8 text-center" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <p className="text-sm" style={{ color: 'var(--color-muted)' }}>
            {tab.charAt(0).toUpperCase() + tab.slice(1)} management is available via the backend API. UI panel coming in the next release.
          </p>
        </div>
      )}

      {/* Modals */}
      <Modal open={modal === 'repay'} onClose={() => { setModal(null); setError('') }} title="Make Repayment" size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => { setModal(null); setError('') }} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={() => handleCommand('repay', { amount: parseFloat(repayAmount) })} disabled={command.isPending || !repayAmount}
              className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
              {command.isPending ? 'Processing…' : 'Submit Repayment'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-4">
          {error && <ErrBox msg={error} />}
          <Field label="Repayment Amount" type="number" min="0.01" step="0.01" value={repayAmount} onChange={e => setRepayAmount(e.target.value)} required />
        </div>
      </Modal>

      <Modal open={modal === 'reject'} onClose={() => setModal(null)} title="Reject Loan" size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setModal(null)} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={() => handleCommand('reject')} className="px-4 py-2 text-sm rounded-lg text-white" style={{ background: 'var(--color-error)' }}>Reject Application</button>
          </div>
        }>
        <p className="p-6 text-sm" style={{ color: 'var(--color-text)' }}>Are you sure you want to reject this loan application? This action cannot be undone.</p>
      </Modal>
    </div>
  )
}

function ActionBtn({ children, onClick, danger }: { children: React.ReactNode; onClick: () => void; danger?: boolean }) {
  return (
    <button onClick={onClick} className="px-3 py-1.5 text-xs font-medium rounded-lg"
      style={{ border: `1px solid ${danger ? 'var(--color-error)' : 'var(--color-border)'}`, color: danger ? 'var(--color-error)' : 'var(--color-text)' }}>
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

function ErrBox({ msg }: { msg: string }) {
  return <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{msg}</p>
}
