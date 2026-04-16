// web-react/src/app/features/accounting/FinancialActivitiesPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import {
  useFinancialActivityAccounts, useCreateFinancialActivityAccount,
  useUpdateFinancialActivityAccount, useDeleteFinancialActivityAccount,
  useGlAccounts,
} from './api/useAccounting'
import type { FinancialActivityAccount, FinancialActivityRequest, FinancialActivityType, GlAccountType, GlAccount } from './api/types'

const ACTIVITIES: Array<{ value: FinancialActivityType; label: string; glType: GlAccountType }> = [
  { value: 'ASSET_FUND_SOURCE',               label: 'Asset — Fund Source',                glType: 'ASSET'     },
  { value: 'ASSET_LOAN_PORTFOLIO',             label: 'Asset — Loan Portfolio',             glType: 'ASSET'     },
  { value: 'ASSET_RECEIVABLE',                 label: 'Asset — Receivable',                 glType: 'ASSET'     },
  { value: 'ASSET_OVERPAYMENT_LIABILITY',      label: 'Asset — Overpayment Liability',      glType: 'LIABILITY' },
  { value: 'LIABILITY_LINKED_TO_FLOAT',        label: 'Liability — Linked to Float',        glType: 'LIABILITY' },
  { value: 'LIABILITY_PAYMENT_GATEWAY',        label: 'Liability — Payment Gateway',        glType: 'LIABILITY' },
  { value: 'LIABILITY_TRANSFER_IN_SUSPENSE',   label: 'Liability — Transfer in Suspense',   glType: 'LIABILITY' },
  { value: 'INCOME_INTEREST',                  label: 'Income — Interest',                  glType: 'INCOME'    },
  { value: 'INCOME_FEE',                       label: 'Income — Fee',                       glType: 'INCOME'    },
  { value: 'EXPENSE_DEPRECIATION',             label: 'Expense — Depreciation',             glType: 'EXPENSE'   },
  { value: 'EXPENSE_LOAN_LOSSES',              label: 'Expense — Loan Losses',              glType: 'EXPENSE'   },
]

const TYPE_VARIANT: Record<GlAccountType, 'info' | 'success' | 'primary' | 'warning' | 'error'> = {
  ASSET:     'info',
  LIABILITY: 'warning',
  EQUITY:    'primary',
  INCOME:    'success',
  EXPENSE:   'error',
}

function blankForm(): FinancialActivityRequest {
  return { financialActivity: 'ASSET_LOAN_PORTFOLIO', glAccountId: '' }
}

export default function FinancialActivitiesPage() {
  const { data, isLoading } = useFinancialActivityAccounts()
  const mappings: FinancialActivityAccount[] = (data as { data: FinancialActivityAccount[] } | undefined)?.data ?? []

  const { data: glData } = useGlAccounts()
  const allGlAccounts: GlAccount[] = (glData as { data: GlAccount[] } | undefined)?.data ?? []

  const createMapping = useCreateFinancialActivityAccount()
  const deleteMapping = useDeleteFinancialActivityAccount()

  const [showModal, setShowModal]   = useState(false)
  const [editing, setEditing]       = useState<FinancialActivityAccount | null>(null)
  const [form, setForm]             = useState<FinancialActivityRequest>(blankForm())
  const [showDelete, setShowDelete] = useState<FinancialActivityAccount | null>(null)
  const [error, setError]           = useState('')

  const update = useUpdateFinancialActivityAccount(editing?.id ?? '')

  // Filter GL accounts to those matching the expected type for the selected activity
  const selectedActivityMeta = ACTIVITIES.find(a => a.value === form.financialActivity)
  const eligibleGlAccounts = allGlAccounts.filter(a =>
    (!selectedActivityMeta || a.accountType === selectedActivityMeta.glType) &&
    a.usage === 'DETAIL' && !a.disabled
  )

  // Track which activities are already mapped
  const mappedActivities = new Set(mappings.map(m => m.financialActivity))
  const availableActivities = ACTIVITIES.filter(a => !mappedActivities.has(a.value) || (editing && editing.financialActivity === a.value))

  function openCreate() {
    setEditing(null)
    const firstUnmapped = availableActivities[0]?.value ?? 'ASSET_LOAN_PORTFOLIO'
    setForm({ financialActivity: firstUnmapped, glAccountId: '' })
    setError(''); setShowModal(true)
  }
  function openEdit(m: FinancialActivityAccount) {
    setEditing(m)
    setForm({ financialActivity: m.financialActivity, glAccountId: m.glAccountId })
    setError(''); setShowModal(true)
  }

  async function save() {
    if (!form.glAccountId) { setError('Select a GL account.'); return }
    setError('')
    try {
      if (editing) { await update.mutateAsync(form) }
      else         { await createMapping.mutateAsync(form) }
      setShowModal(false)
    } catch (err) { setError(err instanceof Error ? err.message : 'Save failed') }
  }

  async function handleDelete() {
    if (!showDelete) return
    try { await deleteMapping.mutateAsync(showDelete.id); setShowDelete(null) }
    catch { /* silent */ }
  }

  // When activity changes, reset GL account selection and update eligible list
  function handleActivityChange(val: FinancialActivityType) {
    setForm({ financialActivity: val, glAccountId: '' })
  }

  const isSaving = createMapping.isPending || update.isPending
  const allMapped = availableActivities.length === 0 && !editing

  return (
    <div>
      <PageHeader title="Financial Activity Accounts"
        actions={
          allMapped && !editing ? undefined :
          <Btn label="+ Map Activity" onClick={openCreate} />
        }
      />
      <p className="text-sm mb-6" style={{ color: 'var(--color-muted)' }}>
        Maps abstract financial activities (loan portfolio, interest income, etc.) to concrete GL account codes.
        Used by the system when auto-posting double-entry journal entries.
      </p>

      {isLoading ? (
        <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
      ) : (
        <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                {['Financial Activity', 'GL Code', 'GL Account Name', 'Account Type', 'Actions'].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {mappings.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>
                    No activity accounts mapped yet. Click &ldquo;+ Map Activity&rdquo; to get started.
                  </td>
                </tr>
              )}
              {mappings.map(m => {
                const meta = ACTIVITIES.find(a => a.value === m.financialActivity)
                return (
                  <tr key={m.id} style={{ borderBottom: '1px solid var(--color-border)', height: 44, background: 'var(--bg-card)' }}>
                    <td className="px-4 py-2 font-medium text-sm" style={{ color: 'var(--color-text)' }}>
                      {meta?.label ?? m.financialActivity}
                    </td>
                    <td className="px-4 py-2 font-mono text-xs" style={{ color: 'var(--color-muted)' }}>{m.glCode}</td>
                    <td className="px-4 py-2" style={{ color: 'var(--color-text)' }}>{m.glAccountName}</td>
                    <td className="px-4 py-2">
                      <StatusBadge label={m.glAccountType} variant={TYPE_VARIANT[m.glAccountType]} />
                    </td>
                    <td className="px-4 py-2">
                      <div className="flex gap-2">
                        <button onClick={() => openEdit(m)} className="text-xs px-2 py-1 rounded"
                          style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Edit</button>
                        <button onClick={() => setShowDelete(m)} className="text-xs px-2 py-1 rounded"
                          style={{ border: '1px solid var(--color-error)', color: 'var(--color-error)' }}>Remove</button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {/* Unmapped activities hint */}
          {availableActivities.length > 0 && (
            <div className="px-4 py-3" style={{ borderTop: '1px solid var(--color-border)', background: 'var(--bg-subtle)' }}>
              <p className="text-xs" style={{ color: 'var(--color-muted)' }}>
                {availableActivities.length} of {ACTIVITIES.length} activities not yet mapped:&nbsp;
                {availableActivities.map(a => a.label).join(', ')}
              </p>
            </div>
          )}
        </div>
      )}

      {/* Create / Edit modal */}
      <Modal open={showModal} onClose={() => setShowModal(false)}
        title={editing ? 'Edit Activity Mapping' : 'Map Financial Activity'} size="md"
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

          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Financial Activity</label>
            <select value={form.financialActivity} onChange={e => handleActivityChange(e.target.value as FinancialActivityType)}
              disabled={!!editing}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none disabled:opacity-60"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
              {(editing ? ACTIVITIES : availableActivities).map(a => (
                <option key={a.value} value={a.value}>{a.label}</option>
              ))}
            </select>
            {selectedActivityMeta && (
              <p className="text-xs mt-1" style={{ color: 'var(--color-muted)' }}>
                Expected GL account type: <strong>{selectedActivityMeta.glType}</strong>
              </p>
            )}
          </div>

          <div>
            <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>GL Account</label>
            <select value={form.glAccountId} onChange={e => setForm(p => ({ ...p, glAccountId: e.target.value }))}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
              <option value="">Select GL account…</option>
              {eligibleGlAccounts.map(a => (
                <option key={a.id} value={a.id}>{a.glCode} — {a.name}</option>
              ))}
            </select>
            {eligibleGlAccounts.length === 0 && form.financialActivity && (
              <p className="text-xs mt-1" style={{ color: 'var(--color-warning)' }}>
                No eligible {selectedActivityMeta?.glType} DETAIL accounts found. Create one in GL Accounts first.
              </p>
            )}
          </div>
        </div>
      </Modal>

      {/* Delete confirm */}
      <Modal open={!!showDelete} onClose={() => setShowDelete(null)} title="Remove Activity Mapping" size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowDelete(null)} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={handleDelete} disabled={deleteMapping.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-error)' }}>
              {deleteMapping.isPending ? 'Removing…' : 'Remove'}
            </button>
          </div>
        }>
        <div className="p-6">
          <p className="text-sm" style={{ color: 'var(--color-text)' }}>
            Remove the mapping for <strong>{ACTIVITIES.find(a => a.value === showDelete?.financialActivity)?.label}</strong>?
            Auto-posting to this GL account will stop until remapped.
          </p>
        </div>
      </Modal>
    </div>
  )
}

function Btn({ label, onClick }: { label: string; onClick: () => void }) {
  return <button onClick={onClick} className="px-4 py-2 rounded-lg text-sm font-medium text-white" style={{ background: 'var(--color-primary)' }}>{label}</button>
}
