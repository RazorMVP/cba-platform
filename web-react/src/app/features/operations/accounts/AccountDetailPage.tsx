// web-react/src/app/features/operations/accounts/AccountDetailPage.tsx
import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { DataTable, type ColumnDef } from '@/shared/components/DataTable'
import { Modal } from '@/shared/components/Modal'
import { useAccount, useAccountTransactions, useAccountCommand, useCreateAccount } from '../api/useAccounts'
import type { Transaction, AccountType } from '../api/types'

type Tab = 'overview' | 'transactions'

const txCols: ColumnDef<Transaction>[] = [
  { key: 'ref', header: 'Reference', cell: r => r.referenceNumber },
  { key: 'type', header: 'Type', cell: r => r.transactionType.replace(/_/g, ' ') },
  {
    key: 'amount', header: 'Amount', numeric: true,
    cell: r => {
      const isCredit = ['CREDIT', 'DEPOSIT', 'TRANSFER_IN'].includes(r.transactionType)
      return (
        <span className="tabular-nums font-medium" style={{ color: isCredit ? 'var(--color-success)' : 'var(--color-error)' }}>
          {isCredit ? '+' : '-'}{Math.abs(r.amount).toLocaleString(undefined, { minimumFractionDigits: 2 })}
        </span>
      )
    },
  },
  {
    key: 'balance', header: 'Running Balance', numeric: true,
    cell: r => <span className="tabular-nums">{r.runningBalance.toLocaleString(undefined, { minimumFractionDigits: 2 })}</span>,
  },
  { key: 'desc', header: 'Description', cell: r => r.description ?? '—' },
  { key: 'date', header: 'Date', numeric: true, cell: r => new Date(r.createdAt).toLocaleString() },
]

export default function AccountDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isNew = id === 'new'
  const [tab, setTab] = useState<Tab>('overview')
  const [txPage, setTxPage] = useState(0)
  const [modal, setModal] = useState<string | null>(null)
  const [cmdAmount, setCmdAmount] = useState('')
  const [cmdDesc, setCmdDesc] = useState('')
  const [error, setError] = useState('')

  const { data: account, isLoading } = useAccount(id ?? '')
  const { data: txData } = useAccountTransactions(id ?? '', { page: txPage, size: 20 })
  const command = useAccountCommand(id ?? '')
  const createAccount = useCreateAccount()

  // New account form state
  const [newForm, setNewForm] = useState({ customerId: '', productId: '', accountType: 'SAVINGS' as AccountType, currencyCode: 'USD' })

  async function handleCommand(cmd: string, body?: Record<string, unknown>) {
    setError('')
    try {
      await command.mutateAsync({ command: cmd, body })
      setModal(null); setCmdAmount(''); setCmdDesc('')
    } catch (err) {
      setError(err instanceof Error ? err.message : `${cmd} failed`)
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault(); setError('')
    try {
      const res = await createAccount.mutateAsync(newForm)
      const newId = (res.data as { data: { id: string } }).data?.id
      if (newId) navigate(`/accounts/${newId}`)
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to open account') }
  }

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  // New account form
  if (isNew) {
    return (
      <div>
        <PageHeader title="Open Account" actions={<Link to="/accounts" className="text-sm" style={{ color: 'var(--color-muted)' }}>← Back</Link>} />
        <form onSubmit={handleCreate} className="rounded-xl p-6 max-w-lg space-y-4" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          {error && <ErrBox msg={error} />}
          <Field label="Customer ID" value={newForm.customerId} onChange={e => setNewForm(f => ({ ...f, customerId: e.target.value }))} required />
          <Field label="Product ID" value={newForm.productId} onChange={e => setNewForm(f => ({ ...f, productId: e.target.value }))} required />
          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Account Type</label>
            <select value={newForm.accountType} onChange={e => setNewForm(f => ({ ...f, accountType: e.target.value as AccountType }))}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
              <option value="SAVINGS">Savings</option>
              <option value="CHECKING">Checking</option>
              <option value="FIXED_DEPOSIT">Fixed Deposit</option>
            </select>
          </div>
          <Field label="Currency" value={newForm.currencyCode} onChange={e => setNewForm(f => ({ ...f, currencyCode: e.target.value }))} />
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={() => navigate('/accounts')} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button type="submit" disabled={createAccount.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
              {createAccount.isPending ? 'Opening…' : 'Open Account'}
            </button>
          </div>
        </form>
      </div>
    )
  }

  if (!account) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Account not found.</div>

  const statusVariant = account.status === 'ACTIVE' ? 'success' : account.status === 'FROZEN' ? 'error' : account.status === 'CLOSED' ? 'neutral' : 'warning'
  const txs = txData?.data ?? []
  const txTotal = txData?.meta?.total ?? 0
  const txPages = Math.ceil(txTotal / 20)

  return (
    <div>
      <PageHeader
        title={account.accountNumber}
        subtitle={`${account.accountType.replace(/_/g, ' ')} · ${account.customerName}`}
        actions={
          <div className="flex items-center gap-3">
            <StatusBadge label={account.status} variant={statusVariant} />
            <Link to="/accounts" className="text-sm" style={{ color: 'var(--color-muted)' }}>← Back</Link>
          </div>
        }
      />

      {/* Balance hero */}
      <div className="rounded-xl p-6 mb-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <p className="text-xs font-semibold uppercase tracking-wider mb-1" style={{ color: 'var(--color-muted)' }}>Available Balance</p>
        <p className="font-display text-3xl font-bold tabular-nums" style={{ color: 'var(--color-text)' }}>
          {account.balance.toLocaleString(undefined, { minimumFractionDigits: 2 })}
          <span className="text-lg ml-2" style={{ color: 'var(--color-muted)' }}>{account.currencyCode}</span>
        </p>
      </div>

      {/* Action buttons */}
      <div className="flex flex-wrap gap-2 mb-6">
        {account.status === 'ACTIVE' && (
          <>
            <ActionBtn onClick={() => setModal('deposit')}>Deposit</ActionBtn>
            <ActionBtn onClick={() => setModal('withdraw')}>Withdraw</ActionBtn>
            <ActionBtn onClick={() => setModal('freeze')} danger>Freeze</ActionBtn>
          </>
        )}
        {account.status === 'FROZEN' && (
          <ActionBtn onClick={() => handleCommand('unfreeze')}>Unfreeze</ActionBtn>
        )}
        {account.status !== 'CLOSED' && account.balance === 0 && (
          <ActionBtn onClick={() => setModal('close')} danger>Close Account</ActionBtn>
        )}
        <ActionBtn onClick={() => setModal('statement')}>Statement</ActionBtn>
      </div>

      {/* Tabs */}
      <TabBar tabs={[{ key: 'overview', label: 'Overview' }, { key: 'transactions', label: 'Transactions' }]} active={tab} onSelect={k => setTab(k as Tab)} />

      {tab === 'overview' && (
        <div className="rounded-xl p-6" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <dl className="grid grid-cols-2 gap-x-8 gap-y-4">
            <InfoRow label="Account Number" value={account.accountNumber} />
            <InfoRow label="Account Type" value={account.accountType.replace(/_/g, ' ')} />
            <InfoRow label="Product" value={account.productName} />
            <InfoRow label="Customer" value={account.customerName} />
            <InfoRow label="Currency" value={account.currencyCode} />
            <InfoRow label="Status" value={account.status} />
            <InfoRow label="Opened" value={new Date(account.createdAt).toLocaleDateString()} />
          </dl>
        </div>
      )}

      {tab === 'transactions' && (
        <div className="rounded-xl" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <DataTable columns={txCols} data={txs} emptyMessage="No transactions yet" getRowKey={r => r.id} />
          {txPages > 1 && (
            <div className="flex items-center justify-between px-6 py-3" style={{ borderTop: '1px solid var(--color-border)' }}>
              <span className="text-xs tabular-nums" style={{ color: 'var(--color-muted)' }}>Page {txPage + 1} of {txPages}</span>
              <div className="flex gap-2">
                <button disabled={txPage === 0} onClick={() => setTxPage(p => p - 1)} className="px-3 py-1 text-xs rounded-md disabled:opacity-40" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>← Prev</button>
                <button disabled={txPage >= txPages - 1} onClick={() => setTxPage(p => p + 1)} className="px-3 py-1 text-xs rounded-md disabled:opacity-40" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Next →</button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Modals */}
      <AmountModal open={modal === 'deposit'} title="Deposit Funds" confirmLabel="Deposit"
        amount={cmdAmount} desc={cmdDesc} error={error} isPending={command.isPending}
        onAmountChange={setCmdAmount} onDescChange={setCmdDesc} onClose={() => { setModal(null); setError('') }}
        onConfirm={() => handleCommand('deposit', { amount: parseFloat(cmdAmount), description: cmdDesc })} />
      <AmountModal open={modal === 'withdraw'} title="Withdraw Funds" confirmLabel="Withdraw"
        amount={cmdAmount} desc={cmdDesc} error={error} isPending={command.isPending}
        onAmountChange={setCmdAmount} onDescChange={setCmdDesc} onClose={() => { setModal(null); setError('') }}
        onConfirm={() => handleCommand('withdraw', { amount: parseFloat(cmdAmount), description: cmdDesc })} />
      <ConfirmModal open={modal === 'freeze'} title="Freeze Account" message="Freeze this account? No transactions will be allowed." confirmLabel="Freeze" danger
        onClose={() => setModal(null)} onConfirm={() => handleCommand('freeze')} />
      <ConfirmModal open={modal === 'close'} title="Close Account" message="Permanently close this account? The balance must be zero." confirmLabel="Close Account" danger
        onClose={() => setModal(null)} onConfirm={() => handleCommand('close')} />
      <StatementModal open={modal === 'statement'} onClose={() => setModal(null)} transactions={txs} />
    </div>
  )
}

// ── Sub-components ───────────────────────────────────────────────

function TabBar({ tabs, active, onSelect }: { tabs: { key: string; label: string }[]; active: string; onSelect: (k: string) => void }) {
  return (
    <div className="flex gap-1 mb-6 rounded-lg p-1 w-fit" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
      {tabs.map(t => (
        <button key={t.key} onClick={() => onSelect(t.key)}
          className="px-4 py-1.5 rounded-md text-sm font-medium transition-colors"
          style={{ background: active === t.key ? 'var(--color-primary)' : 'transparent', color: active === t.key ? '#fff' : 'var(--color-muted)' }}>
          {t.label}
        </button>
      ))}
    </div>
  )
}

function ActionBtn({ children, onClick, danger }: { children: React.ReactNode; onClick: () => void; danger?: boolean }) {
  return (
    <button onClick={onClick} className="px-3 py-1.5 text-xs font-medium rounded-lg transition-colors"
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

function AmountModal({ open, title, confirmLabel, amount, desc, error, isPending, onAmountChange, onDescChange, onClose, onConfirm }: {
  open: boolean; title: string; confirmLabel: string; amount: string; desc: string; error: string; isPending: boolean
  onAmountChange: (v: string) => void; onDescChange: (v: string) => void; onClose: () => void; onConfirm: () => void
}) {
  return (
    <Modal open={open} onClose={onClose} title={title} size="sm"
      footer={
        <div className="flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
          <button onClick={onConfirm} disabled={isPending || !amount} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
            {isPending ? 'Processing…' : confirmLabel}
          </button>
        </div>
      }>
      <div className="p-6 space-y-4">
        {error && <ErrBox msg={error} />}
        <Field label="Amount" type="number" min="0.01" step="0.01" value={amount} onChange={e => onAmountChange(e.target.value)} required />
        <Field label="Description (optional)" value={desc} onChange={e => onDescChange(e.target.value)} />
      </div>
    </Modal>
  )
}

function ConfirmModal({ open, title, message, confirmLabel, danger, onClose, onConfirm }: {
  open: boolean; title: string; message: string; confirmLabel: string; danger?: boolean; onClose: () => void; onConfirm: () => void
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

function StatementModal({ open, onClose, transactions }: {
  open: boolean; onClose: () => void; transactions: Transaction[]
}) {
  const credits = transactions.filter(t => ['CREDIT', 'DEPOSIT', 'TRANSFER_IN'].includes(t.transactionType)).reduce((s, t) => s + t.amount, 0)
  const debits = transactions.filter(t => !['CREDIT', 'DEPOSIT', 'TRANSFER_IN'].includes(t.transactionType)).reduce((s, t) => s + t.amount, 0)

  return (
    <Modal open={open} onClose={onClose} title="Account Statement" size="lg"
      footer={<div className="flex justify-end"><button onClick={onClose} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Close</button></div>}>
      <div className="p-6">
        <div className="grid grid-cols-3 gap-4 mb-6">
          {[
            { label: 'Total Credits', value: credits, color: 'var(--color-success)' },
            { label: 'Total Debits', value: debits, color: 'var(--color-error)' },
            { label: 'Net', value: credits - debits, color: 'var(--color-text)' },
          ].map(item => (
            <div key={item.label} className="p-4 rounded-lg" style={{ background: 'var(--bg-subtle)' }}>
              <p className="text-xs" style={{ color: 'var(--color-muted)' }}>{item.label}</p>
              <p className="font-display font-bold tabular-nums text-lg" style={{ color: item.color }}>
                {item.value.toLocaleString(undefined, { minimumFractionDigits: 2 })}
              </p>
            </div>
          ))}
        </div>
        <DataTable columns={txCols} data={transactions} emptyMessage="No transactions" getRowKey={r => r.id} />
      </div>
    </Modal>
  )
}
