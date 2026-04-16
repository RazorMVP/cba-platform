// web-react/src/app/features/accounting/ProvisioningPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { Modal } from '@/shared/components/Modal'
import {
  useProvisioningCriteria, useCreateProvisioningCriteria,
  useUpdateProvisioningCriteria, useDeleteProvisioningCriteria,
  useGlAccounts,
} from './api/useAccounting'
import type { ProvisioningCriteria, ProvisioningCriteriaRequest, ProvisioningDefinition, GlAccount } from './api/types'

const DEFAULT_BANDS: Omit<ProvisioningDefinition, 'liabilityAccountId' | 'expenseAccountId'>[] = [
  { categoryName: 'STANDARD',     minAge: 0,   maxAge: 30,  provisionPercentage: 1   },
  { categoryName: 'WATCH',        minAge: 31,  maxAge: 90,  provisionPercentage: 5   },
  { categoryName: 'SUB_STANDARD', minAge: 91,  maxAge: 180, provisionPercentage: 25  },
  { categoryName: 'DOUBTFUL',     minAge: 181, maxAge: 360, provisionPercentage: 50  },
  { categoryName: 'LOSS',         minAge: 361, maxAge: 999, provisionPercentage: 100 },
]

function blankDef(band: typeof DEFAULT_BANDS[0]): ProvisioningDefinition {
  return { ...band, liabilityAccountId: '', expenseAccountId: '' }
}

function blankForm(): ProvisioningCriteriaRequest {
  return { criteriaName: '', definitions: DEFAULT_BANDS.map(blankDef) }
}

export default function ProvisioningPage() {
  const { data, isLoading } = useProvisioningCriteria()
  const criteria: ProvisioningCriteria[] = (data as { data: ProvisioningCriteria[] } | undefined)?.data ?? []

  const { data: glData } = useGlAccounts()
  const glAccounts: GlAccount[] = (glData as { data: GlAccount[] } | undefined)?.data ?? []
  const liabilityAccounts = glAccounts.filter(a => a.accountType === 'LIABILITY' && a.usage === 'DETAIL' && !a.disabled)
  const expenseAccounts   = glAccounts.filter(a => a.accountType === 'EXPENSE'   && a.usage === 'DETAIL' && !a.disabled)

  const createCriteria = useCreateProvisioningCriteria()
  const deleteCriteria = useDeleteProvisioningCriteria()

  const [showModal, setShowModal]   = useState(false)
  const [editing, setEditing]       = useState<ProvisioningCriteria | null>(null)
  const [form, setForm]             = useState<ProvisioningCriteriaRequest>(blankForm())
  const [showDelete, setShowDelete] = useState<ProvisioningCriteria | null>(null)
  const [error, setError]           = useState('')

  const update = useUpdateProvisioningCriteria(editing?.id ?? '')

  function openCreate() { setEditing(null); setForm(blankForm()); setError(''); setShowModal(true) }
  function openEdit(c: ProvisioningCriteria) {
    setEditing(c)
    setForm({
      criteriaName: c.criteriaName,
      definitions: c.definitions.map(d => ({
        categoryName: d.categoryName, minAge: d.minAge, maxAge: d.maxAge,
        provisionPercentage: d.provisionPercentage,
        liabilityAccountId: d.liabilityAccountId, expenseAccountId: d.expenseAccountId,
      })),
    })
    setError(''); setShowModal(true)
  }

  async function save() {
    setError('')
    try {
      if (editing) { await update.mutateAsync(form) }
      else         { await createCriteria.mutateAsync(form) }
      setShowModal(false)
    } catch (err) { setError(err instanceof Error ? err.message : 'Save failed') }
  }

  async function handleDelete() {
    if (!showDelete) return
    try { await deleteCriteria.mutateAsync(showDelete.id); setShowDelete(null) }
    catch { /* silent */ }
  }

  function updateDef(idx: number, field: keyof ProvisioningDefinition, val: string | number) {
    setForm(p => ({ ...p, definitions: p.definitions.map((d, i) => i === idx ? { ...d, [field]: val } : d) }))
  }

  const isSaving = createCriteria.isPending || update.isPending

  return (
    <div>
      <PageHeader title="Provisioning Criteria" actions={<Btn label="+ New Criteria" onClick={openCreate} />} />
      <p className="text-sm mb-6" style={{ color: 'var(--color-muted)' }}>
        IFRS 9 / Basel II loan loss provisioning. Each criteria defines age bands with provision percentages and the GL accounts to post to.
      </p>

      {isLoading ? (
        <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
      ) : criteria.length === 0 ? (
        <div className="p-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>No provisioning criteria defined.</div>
      ) : (
        <div className="space-y-4">
          {criteria.map(c => (
            <div key={c.id} className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
              {/* Criteria header */}
              <div className="flex items-center justify-between px-4 py-3" style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-subtle)' }}>
                <div>
                  <p className="text-sm font-semibold" style={{ color: 'var(--color-text)' }}>{c.criteriaName}</p>
                  {c.createdBy && <p className="text-xs" style={{ color: 'var(--color-muted)' }}>Created by {c.createdBy}</p>}
                </div>
                <div className="flex gap-2">
                  <button onClick={() => openEdit(c)} className="text-xs px-3 py-1.5 rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Edit</button>
                  <button onClick={() => setShowDelete(c)} className="text-xs px-3 py-1.5 rounded-lg" style={{ border: '1px solid var(--color-error)', color: 'var(--color-error)' }}>Delete</button>
                </div>
              </div>
              {/* Age bands table */}
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--color-border)' }}>
                    {['Category', 'Age Range (days)', 'Provision %', 'Liability GL', 'Expense GL'].map(h => (
                      <th key={h} className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {c.definitions.map((d, i) => (
                    <tr key={i} style={{ borderBottom: '1px solid var(--color-border)' }}>
                      <td className="px-4 py-2 font-medium text-xs" style={{ color: 'var(--color-text)' }}>
                        <span className="px-2 py-0.5 rounded text-xs" style={{ background: bandColor(d.categoryName).bg, color: bandColor(d.categoryName).fg }}>
                          {d.categoryName.replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td className="px-4 py-2 tabular-nums text-sm" style={{ color: 'var(--color-text)' }}>{d.minAge} – {d.maxAge}</td>
                      <td className="px-4 py-2 tabular-nums font-semibold text-sm" style={{ color: 'var(--color-text)' }}>{d.provisionPercentage}%</td>
                      <td className="px-4 py-2 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>{d.liabilityAccountCode ?? d.liabilityAccountId}</td>
                      <td className="px-4 py-2 text-xs font-mono" style={{ color: 'var(--color-muted)' }}>{d.expenseAccountCode ?? d.expenseAccountId}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
        </div>
      )}

      {/* Create / Edit modal */}
      <Modal open={showModal} onClose={() => setShowModal(false)}
        title={editing ? `Edit: ${editing.criteriaName}` : 'New Provisioning Criteria'} size="lg"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowModal(false)} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={save} disabled={isSaving} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
              {isSaving ? 'Saving…' : 'Save'}
            </button>
          </div>
        }>
        <div className="p-6 space-y-5">
          {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
          <Field label="Criteria Name" value={form.criteriaName} onChange={e => setForm(p => ({ ...p, criteriaName: e.target.value }))} required />
          <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>Age Bands</p>
          <div className="space-y-3">
            {form.definitions.map((d, i) => (
              <div key={i} className="rounded-lg p-3 space-y-3" style={{ border: '1px solid var(--color-border)', background: 'var(--bg-subtle)' }}>
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-xs font-semibold px-2 py-0.5 rounded" style={{ background: bandColor(d.categoryName).bg, color: bandColor(d.categoryName).fg }}>
                    {d.categoryName.replace(/_/g, ' ')}
                  </span>
                  <span className="text-xs" style={{ color: 'var(--color-muted)' }}>{d.minAge}–{d.maxAge} days</span>
                </div>
                <div className="grid grid-cols-3 gap-3">
                  <Field label="Provision %" type="number" min="0" max="100" step="0.01" value={String(d.provisionPercentage)}
                    onChange={e => updateDef(i, 'provisionPercentage', parseFloat(e.target.value) || 0)} />
                  <div>
                    <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Liability GL Account</label>
                    <select value={d.liabilityAccountId} onChange={e => updateDef(i, 'liabilityAccountId', e.target.value)}
                      className="w-full px-3 py-2 rounded-lg text-sm outline-none"
                      style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
                      <option value="">Select…</option>
                      {liabilityAccounts.map(a => <option key={a.id} value={a.id}>{a.glCode} — {a.name}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Expense GL Account</label>
                    <select value={d.expenseAccountId} onChange={e => updateDef(i, 'expenseAccountId', e.target.value)}
                      className="w-full px-3 py-2 rounded-lg text-sm outline-none"
                      style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
                      <option value="">Select…</option>
                      {expenseAccounts.map(a => <option key={a.id} value={a.id}>{a.glCode} — {a.name}</option>)}
                    </select>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </Modal>

      {/* Delete confirm */}
      <Modal open={!!showDelete} onClose={() => setShowDelete(null)} title="Delete Provisioning Criteria" size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowDelete(null)} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
            <button onClick={handleDelete} disabled={deleteCriteria.isPending} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-error)' }}>
              {deleteCriteria.isPending ? 'Deleting…' : 'Delete'}
            </button>
          </div>
        }>
        <div className="p-6">
          <p className="text-sm" style={{ color: 'var(--color-text)' }}>Delete <strong>{showDelete?.criteriaName}</strong>? This cannot be undone.</p>
        </div>
      </Modal>
    </div>
  )
}

function bandColor(name: string): { bg: string; fg: string } {
  if (name === 'STANDARD')     return { bg: 'var(--color-success-bg)', fg: 'var(--color-success)' }
  if (name === 'WATCH')        return { bg: 'var(--color-info-bg)',    fg: 'var(--color-info)' }
  if (name === 'SUB_STANDARD') return { bg: 'var(--color-warning-bg)', fg: 'var(--color-warning)' }
  if (name === 'DOUBTFUL')     return { bg: 'var(--color-warning-bg)', fg: 'var(--color-warning)' }
  if (name === 'LOSS')         return { bg: 'var(--color-error-bg)',   fg: 'var(--color-error)' }
  return { bg: 'var(--bg-subtle)', fg: 'var(--color-muted)' }
}

function Btn({ label, onClick }: { label: string; onClick: () => void }) {
  return <button onClick={onClick} className="px-4 py-2 rounded-lg text-sm font-medium text-white" style={{ background: 'var(--color-primary)' }}>{label}</button>
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
