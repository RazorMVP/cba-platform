// web-react/src/app/features/accounting/GlAccountsPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useGlAccounts, useCreateGlAccount, useUpdateGlAccount, useGlAccountCommand } from './api/useAccounting'
import type { GlAccount, GlAccountRequest, GlAccountType, GlAccountUsage } from './api/types'

const TYPE_TABS: Array<'ALL' | GlAccountType> = ['ALL', 'ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE']

const TYPE_VARIANT: Record<GlAccountType, 'info' | 'success' | 'primary' | 'warning' | 'error'> = {
  ASSET:     'info',
  LIABILITY: 'warning',
  EQUITY:    'primary',
  INCOME:    'success',
  EXPENSE:   'error',
}

function blankForm(): GlAccountRequest {
  return { glCode: '', name: '', accountType: 'ASSET', usage: 'DETAIL', manualEntriesAllowed: true, description: '' }
}

export default function GlAccountsPage() {
  const { data, isLoading } = useGlAccounts()
  const accounts: GlAccount[] = (data as { data: GlAccount[] } | undefined)?.data ?? []

  const create = useCreateGlAccount()

  const [typeFilter, setTypeFilter] = useState<'ALL' | GlAccountType>('ALL')
  const [search, setSearch]         = useState('')
  const [showModal, setShowModal]   = useState(false)
  const [editing, setEditing]       = useState<GlAccount | null>(null)
  const [form, setForm]             = useState<GlAccountRequest>(blankForm())
  const [error, setError]           = useState('')

  const update = useUpdateGlAccount(editing?.id ?? '')
  const cmd    = useGlAccountCommand(editing?.id ?? '')

  const filtered = accounts.filter(a => {
    const matchType   = typeFilter === 'ALL' || a.accountType === typeFilter
    const matchSearch = !search || a.name.toLowerCase().includes(search.toLowerCase()) || a.glCode.toLowerCase().includes(search.toLowerCase())
    return matchType && matchSearch
  })

  function openCreate() { setEditing(null); setForm(blankForm()); setError(''); setShowModal(true) }
  function openEdit(a: GlAccount) {
    setEditing(a)
    setForm({ glCode: a.glCode, name: a.name, accountType: a.accountType, usage: a.usage, manualEntriesAllowed: a.manualEntriesAllowed, description: a.description ?? '' })
    setError('')
    setShowModal(true)
  }

  async function save() {
    setError('')
    try {
      if (editing) { await update.mutateAsync(form) }
      else         { await create.mutateAsync(form) }
      setShowModal(false)
    } catch (err) { setError(err instanceof Error ? err.message : 'Save failed') }
  }

  async function toggleStatus(a: GlAccount) {
    try { await (a.disabled ? cmd : cmd).mutateAsync(a.disabled ? 'enable' : 'disable') }
    catch { /* silent */ }
  }

  const isSaving = create.isPending || update.isPending

  return (
    <div>
      <PageHeader title="GL Accounts" actions={<Btn label="+ New Account" onClick={openCreate} />} />

      {/* Type filter tabs */}
      <div className="flex gap-1 mb-4 border-b" style={{ borderColor: 'var(--color-border)' }}>
        {TYPE_TABS.map(t => (
          <button key={t} onClick={() => setTypeFilter(t)}
            className="px-4 py-2 text-sm font-medium capitalize transition-colors border-b-2 -mb-px"
            style={{ borderColor: typeFilter === t ? 'var(--color-primary)' : 'transparent', color: typeFilter === t ? 'var(--color-primary)' : 'var(--color-muted)' }}>
            {t === 'ALL' ? 'All Types' : t.charAt(0) + t.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {/* Search */}
      <div className="mb-4">
        <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search by name or GL code…"
          className="w-full max-w-sm px-3 py-2 rounded-lg text-sm outline-none"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
      </div>

      {/* Table */}
      {isLoading ? (
        <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
      ) : (
        <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                {['GL Code', 'Name', 'Type', 'Usage', 'Manual Entries', 'Parent', 'Status', 'Actions'].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 && (
                <tr><td colSpan={8} className="px-4 py-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>No GL accounts found.</td></tr>
              )}
              {filtered.map(a => (
                <tr key={a.id} style={{ borderBottom: '1px solid var(--color-border)', height: 44, background: 'var(--bg-card)' }}>
                  <td className="px-4 py-2 font-mono text-xs" style={{ color: 'var(--color-text)' }}>{a.glCode}</td>
                  <td className="px-4 py-2 font-medium" style={{ color: 'var(--color-text)' }}>{a.name}</td>
                  <td className="px-4 py-2">
                    <StatusBadge label={a.accountType} variant={TYPE_VARIANT[a.accountType]} />
                  </td>
                  <td className="px-4 py-2 text-xs" style={{ color: 'var(--color-muted)' }}>{a.usage}</td>
                  <td className="px-4 py-2">
                    <span className="text-xs px-2 py-0.5 rounded" style={{ background: a.manualEntriesAllowed ? 'var(--color-success-bg)' : 'var(--bg-subtle)', color: a.manualEntriesAllowed ? 'var(--color-success)' : 'var(--color-muted)' }}>
                      {a.manualEntriesAllowed ? 'Allowed' : 'Not allowed'}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-xs" style={{ color: 'var(--color-muted)' }}>{a.parentName ?? '—'}</td>
                  <td className="px-4 py-2">
                    <StatusBadge label={a.disabled ? 'Disabled' : 'Enabled'} variant={a.disabled ? 'neutral' : 'success'} />
                  </td>
                  <td className="px-4 py-2">
                    <div className="flex gap-2">
                      <button onClick={() => openEdit(a)} className="text-xs px-2 py-1 rounded" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Edit</button>
                      <button onClick={() => toggleStatus(a)} className="text-xs px-2 py-1 rounded"
                        style={{ border: '1px solid var(--color-border)', color: a.disabled ? 'var(--color-success)' : 'var(--color-error)' }}>
                        {a.disabled ? 'Enable' : 'Disable'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create / Edit modal */}
      <Modal open={showModal} onClose={() => setShowModal(false)}
        title={editing ? `Edit: ${editing.name}` : 'New GL Account'} size="md"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowModal(false)} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={save} disabled={isSaving} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
              {isSaving ? 'Saving…' : 'Save'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-4">
          {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
          <div className="grid grid-cols-2 gap-4">
            <Field label="GL Code" value={form.glCode} onChange={e => setForm(p => ({ ...p, glCode: e.target.value }))} required />
            <Field label="Account Name" value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} required />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Select label="Account Type" value={form.accountType} onChange={v => setForm(p => ({ ...p, accountType: v as GlAccountType }))}
              options={['ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE'].map(v => ({ label: v.charAt(0) + v.slice(1).toLowerCase(), value: v }))} />
            <Select label="Usage" value={form.usage} onChange={v => setForm(p => ({ ...p, usage: v as GlAccountUsage }))}
              options={[{ label: 'Detail (postable)', value: 'DETAIL' }, { label: 'Header (group)', value: 'HEADER' }]} />
          </div>
          <Field label="Description (optional)" value={form.description ?? ''} onChange={e => setForm(p => ({ ...p, description: e.target.value }))} />
          <label className="flex items-center gap-2 text-sm cursor-pointer" style={{ color: 'var(--color-text)' }}>
            <input type="checkbox" checked={form.manualEntriesAllowed} onChange={e => setForm(p => ({ ...p, manualEntriesAllowed: e.target.checked }))} className="w-4 h-4" />
            Allow manual journal entries to this account
          </label>
        </div>
      </Modal>
    </div>
  )
}

function Btn({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button onClick={onClick} className="px-4 py-2 rounded-lg text-sm font-medium text-white" style={{ background: 'var(--color-primary)' }}>
      {label}
    </button>
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
function Select({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: { label: string; value: string }[] }) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <select value={value} onChange={e => onChange(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </div>
  )
}
