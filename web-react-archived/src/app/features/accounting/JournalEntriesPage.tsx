// web-react/src/app/features/accounting/JournalEntriesPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { Modal } from '@/shared/components/Modal'
import { useJournalEntries, useCreateManualJournalEntry, useReverseJournalEntry, useGlAccounts } from './api/useAccounting'
import type { JournalEntry, ManualJournalLine, GlAccount } from './api/types'

function today() { return new Date().toISOString().slice(0, 10) }
function monthAgo() {
  const d = new Date(); d.setMonth(d.getMonth() - 1)
  return d.toISOString().slice(0, 10)
}

function blankLine(): ManualJournalLine { return { glAccountId: '', amount: 0 } }

export default function JournalEntriesPage() {
  const [from, setFrom] = useState(monthAgo())
  const [to, setTo]     = useState(today())

  const { data, isLoading } = useJournalEntries(from, to)
  const entries: JournalEntry[] = (data as { data: JournalEntry[] } | undefined)?.data ?? []

  const { data: glData } = useGlAccounts()
  const glAccounts: GlAccount[] = (glData as { data: GlAccount[] } | undefined)?.data ?? []
  const detailAccounts = glAccounts.filter(a => a.usage === 'DETAIL' && a.manualEntriesAllowed && !a.disabled)

  const createEntry = useCreateManualJournalEntry()
  const reverse     = useReverseJournalEntry()

  const [showModal, setShowModal] = useState(false)
  const [txDate, setTxDate]       = useState(today())
  const [refNum, setRefNum]       = useState('')
  const [comments, setComments]   = useState('')
  const [debits, setDebits]       = useState<ManualJournalLine[]>([blankLine()])
  const [credits, setCredits]     = useState<ManualJournalLine[]>([blankLine()])
  const [error, setError]         = useState('')

  // Group entries by transactionId for T-ledger view
  const grouped = entries.reduce<Record<string, JournalEntry[]>>((acc, e) => {
    ;(acc[e.transactionId] ??= []).push(e)
    return acc
  }, {})
  const txIds = Object.keys(grouped).sort((a, b) => {
    const dateA = grouped[a][0].entryDate
    const dateB = grouped[b][0].entryDate
    return dateB.localeCompare(dateA)
  })

  const debitTotal  = debits.reduce((s, l) => s + (parseFloat(String(l.amount)) || 0), 0)
  const creditTotal = credits.reduce((s, l) => s + (parseFloat(String(l.amount)) || 0), 0)
  const balanced    = Math.abs(debitTotal - creditTotal) < 0.001 && debitTotal > 0

  function openModal() {
    setTxDate(today()); setRefNum(''); setComments('')
    setDebits([blankLine()]); setCredits([blankLine()])
    setError(''); setShowModal(true)
  }

  async function save() {
    if (!balanced) { setError('Debits and credits must balance.'); return }
    setError('')
    try {
      await createEntry.mutateAsync({
        transactionDate: txDate, locale: 'en', dateFormat: 'yyyy-MM-dd',
        referenceNumber: refNum || undefined, comments: comments || undefined,
        debits: debits.filter(l => l.glAccountId && l.amount > 0),
        credits: credits.filter(l => l.glAccountId && l.amount > 0),
      })
      setShowModal(false)
    } catch (err) { setError(err instanceof Error ? err.message : 'Save failed') }
  }

  async function handleReverse(e: JournalEntry) {
    if (!window.confirm(`Reverse journal entry ${e.transactionId}?`)) return
    try { await reverse.mutateAsync(e.id) } catch { /* silent */ }
  }

  function updateLine(side: 'debit' | 'credit', idx: number, field: keyof ManualJournalLine, val: string) {
    const setter = side === 'debit' ? setDebits : setCredits
    setter(prev => prev.map((l, i) => i === idx ? { ...l, [field]: field === 'amount' ? parseFloat(val) || 0 : val } : l))
  }
  function addLine(side: 'debit' | 'credit') {
    (side === 'debit' ? setDebits : setCredits)(prev => [...prev, blankLine()])
  }
  function removeLine(side: 'debit' | 'credit', idx: number) {
    (side === 'debit' ? setDebits : setCredits)(prev => prev.filter((_, i) => i !== idx))
  }

  return (
    <div>
      <PageHeader title="Journal Entries" actions={<Btn label="+ Manual Entry" onClick={openModal} />} />

      {/* Date range filter */}
      <div className="flex items-center gap-3 mb-6">
        <div className="flex items-center gap-2">
          <label className="text-xs font-medium" style={{ color: 'var(--color-muted)' }}>From</label>
          <input type="date" value={from} onChange={e => setFrom(e.target.value)}
            className="px-3 py-1.5 rounded-lg text-sm outline-none"
            style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
        </div>
        <div className="flex items-center gap-2">
          <label className="text-xs font-medium" style={{ color: 'var(--color-muted)' }}>To</label>
          <input type="date" value={to} onChange={e => setTo(e.target.value)}
            className="px-3 py-1.5 rounded-lg text-sm outline-none"
            style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
        </div>
        <span className="text-xs" style={{ color: 'var(--color-muted)' }}>
          {entries.length} entries in {txIds.length} transactions
        </span>
      </div>

      {isLoading ? (
        <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
      ) : txIds.length === 0 ? (
        <div className="p-8 text-sm text-center" style={{ color: 'var(--color-muted)' }}>No journal entries in this date range.</div>
      ) : (
        <div className="space-y-3">
          {txIds.map(txId => {
            const lines = grouped[txId]
            const first = lines[0]
            const isReversed = lines.some(l => l.reversed)
            const canReverse = !isReversed && first.createdByType === 'USER' && !first.reversalId
            const debitSum = lines.filter(l => l.type === 'DEBIT').reduce((s, l) => s + l.amount, 0)
            return (
              <div key={txId} className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                {/* Transaction header */}
                <div className="flex items-center justify-between px-4 py-3" style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-subtle)' }}>
                  <div className="flex items-center gap-3">
                    <span className="text-xs font-mono" style={{ color: 'var(--color-muted)' }}>{txId}</span>
                    <span className="text-xs font-medium" style={{ color: 'var(--color-text)' }}>{new Date(first.entryDate).toLocaleDateString()}</span>
                    {first.referenceNumber && <span className="text-xs px-2 py-0.5 rounded" style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>Ref: {first.referenceNumber}</span>}
                    <span className="text-xs px-2 py-0.5 rounded" style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)' }}>
                      {first.createdByType === 'USER' ? 'Manual' : 'System'}
                    </span>
                    {isReversed && <StatusBadge label="Reversed" variant="neutral" />}
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="text-sm font-semibold tabular-nums" style={{ color: 'var(--color-text)' }}>
                      {debitSum.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                    </span>
                    {canReverse && (
                      <button onClick={() => handleReverse(first)} className="text-xs px-2 py-1 rounded"
                        style={{ border: '1px solid var(--color-error)', color: 'var(--color-error)' }}>
                        Reverse
                      </button>
                    )}
                  </div>
                </div>
                {/* Entry lines */}
                <table className="w-full text-sm">
                  <tbody>
                    {lines.map(e => (
                      <tr key={e.id} style={{ borderBottom: '1px solid var(--color-border)' }}>
                        <td className="px-4 py-2 w-28">
                          <StatusBadge label={e.type} variant={e.type === 'DEBIT' ? 'error' : 'success'} />
                        </td>
                        <td className="px-4 py-2 font-mono text-xs" style={{ color: 'var(--color-muted)' }}>{e.glAccountCode}</td>
                        <td className="px-4 py-2" style={{ color: 'var(--color-text)' }}>{e.glAccountName}</td>
                        <td className="px-4 py-2 tabular-nums text-right font-medium" style={{ color: 'var(--color-text)' }}>
                          {e.amount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                        <td className="px-4 py-2 text-xs" style={{ color: 'var(--color-muted)' }}>{e.comments ?? ''}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          })}
        </div>
      )}

      {/* Manual entry modal */}
      <Modal open={showModal} onClose={() => setShowModal(false)} title="New Manual Journal Entry" size="lg"
        footer={
          <div className="flex items-center justify-between">
            <span className="text-sm" style={{ color: balanced ? 'var(--color-success)' : 'var(--color-error)' }}>
              {balanced ? '✓ Balanced' : `Δ ${Math.abs(debitTotal - creditTotal).toLocaleString(undefined, { minimumFractionDigits: 2 })}`}
            </span>
            <div className="flex gap-3">
              <button onClick={() => setShowModal(false)} className="px-4 py-2 text-sm rounded-lg" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>Cancel</button>
              <button onClick={save} disabled={createEntry.isPending || !balanced} className="px-4 py-2 text-sm rounded-lg text-white disabled:opacity-60" style={{ background: 'var(--color-primary)' }}>
                {createEntry.isPending ? 'Posting…' : 'Post Entry'}
              </button>
            </div>
          </div>
        }>
        <div className="p-6 space-y-5">
          {error && <p className="text-sm p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
          <div className="grid grid-cols-3 gap-4">
            <MField label="Transaction Date" type="date" value={txDate} onChange={e => setTxDate(e.target.value)} />
            <MField label="Reference # (optional)" value={refNum} onChange={e => setRefNum(e.target.value)} />
            <MField label="Comments (optional)" value={comments} onChange={e => setComments(e.target.value)} />
          </div>

          {/* Debits */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-error)' }}>Debits — {debitTotal.toLocaleString(undefined, { minimumFractionDigits: 2 })}</p>
              <button onClick={() => addLine('debit')} className="text-xs px-2 py-0.5 rounded" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>+ Line</button>
            </div>
            {debits.map((l, i) => (
              <JournalLineRow key={i} line={l} accounts={detailAccounts} onChange={(f, v) => updateLine('debit', i, f, v)}
                onRemove={debits.length > 1 ? () => removeLine('debit', i) : undefined} />
            ))}
          </div>

          {/* Credits */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-success)' }}>Credits — {creditTotal.toLocaleString(undefined, { minimumFractionDigits: 2 })}</p>
              <button onClick={() => addLine('credit')} className="text-xs px-2 py-0.5 rounded" style={{ border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>+ Line</button>
            </div>
            {credits.map((l, i) => (
              <JournalLineRow key={i} line={l} accounts={detailAccounts} onChange={(f, v) => updateLine('credit', i, f, v)}
                onRemove={credits.length > 1 ? () => removeLine('credit', i) : undefined} />
            ))}
          </div>
        </div>
      </Modal>
    </div>
  )
}

function JournalLineRow({ line, accounts, onChange, onRemove }: {
  line: ManualJournalLine
  accounts: GlAccount[]
  onChange: (f: keyof ManualJournalLine, v: string) => void
  onRemove?: () => void
}) {
  return (
    <div className="flex gap-2 mb-2 items-end">
      <div className="flex-1">
        <select value={line.glAccountId} onChange={e => onChange('glAccountId', e.target.value)}
          className="w-full px-3 py-2 rounded-lg text-sm outline-none"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
          <option value="">Select GL account…</option>
          {accounts.map(a => <option key={a.id} value={a.id}>{a.glCode} — {a.name}</option>)}
        </select>
      </div>
      <div className="w-32">
        <input type="number" min="0" step="0.01" value={line.amount || ''} onChange={e => onChange('amount', e.target.value)}
          placeholder="Amount" className="w-full px-3 py-2 rounded-lg text-sm outline-none"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
      </div>
      <div className="w-36">
        <input value={line.comments ?? ''} onChange={e => onChange('comments', e.target.value)}
          placeholder="Note (optional)" className="w-full px-3 py-2 rounded-lg text-sm outline-none"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
      </div>
      {onRemove && (
        <button onClick={onRemove} className="pb-0.5 text-lg leading-none" style={{ color: 'var(--color-error)' }}>×</button>
      )}
    </div>
  )
}

function Btn({ label, onClick }: { label: string; onClick: () => void }) {
  return <button onClick={onClick} className="px-4 py-2 rounded-lg text-sm font-medium text-white" style={{ background: 'var(--color-primary)' }}>{label}</button>
}
function MField({ label, ...props }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <input {...props} className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
    </div>
  )
}
