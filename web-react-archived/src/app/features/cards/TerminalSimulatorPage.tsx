// web-react/src/app/features/cards/TerminalSimulatorPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { useSimulate } from './api/useCards'
import type { SimulateRequest, SimulateResponse, EntryMode } from './api/types'

type TxnType = 'purchase' | 'withdrawal' | 'balance' | 'reversal' | 'network/signon' | 'network/echo'

const TXN_TYPES: Array<{ value: TxnType; label: string }> = [
  { value: 'purchase',        label: 'Purchase' },
  { value: 'withdrawal',      label: 'ATM Withdrawal' },
  { value: 'balance',         label: 'Balance Enquiry' },
  { value: 'reversal',        label: 'Reversal' },
  { value: 'network/signon',  label: 'Network Sign-On' },
  { value: 'network/echo',    label: 'Echo Test' },
]

const ENTRY_MODES: EntryMode[] = ['CHIP', 'SWIPE', 'CONTACTLESS']

const RC_LABEL: Record<string, string> = {
  '00': 'Approved', '05': 'Do Not Honor', '51': 'Insufficient Funds',
  '54': 'Expired Card', '57': 'Not Permitted', '62': 'Restricted', '91': 'Issuer Unavailable',
}

function blankForm(): SimulateRequest {
  return {
    cardNumber: '', expiryDate: '', amount: 0, currency: '840',
    terminalId: 'TERM0001', merchantId: 'MERCH000000001',
    merchantName: 'Test Merchant', entryMode: 'CHIP',
  }
}

export default function TerminalSimulatorPage() {
  const [txnType, setTxnType]       = useState<TxnType>('purchase')
  const [form, setForm]             = useState<SimulateRequest>(blankForm())
  const [result, setResult]         = useState<SimulateResponse | null>(null)
  const [hexOpen, setHexOpen]       = useState(false)
  const [error, setError]           = useState('')

  const simulate = useSimulate(txnType)

  async function send() {
    setError('')
    setResult(null)
    try {
      const res = await simulate.mutateAsync(form)
      setResult(res as SimulateResponse)
    } catch (e) { setError(e instanceof Error ? e.message : 'Request failed') }
  }

  const isApproved = result?.approved === true
  const isNetworkTxn = txnType.startsWith('network/')

  return (
    <div>
      <PageHeader
        title="Terminal Simulator"
        subtitle="Send ISO 8583 test transactions to the Front End Processor"
      />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left — form */}
        <div className="rounded-xl p-5" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <p className="text-sm font-semibold mb-4" style={{ color: 'var(--color-text)' }}>Transaction</p>

          {/* Transaction type */}
          <div className="flex flex-wrap gap-2 mb-4">
            {TXN_TYPES.map(t => (
              <button key={t.value} onClick={() => setTxnType(t.value)}
                className="px-3 py-1.5 rounded-full text-xs font-medium transition-colors"
                style={{
                  background: txnType === t.value ? 'var(--color-primary)' : 'var(--bg-subtle)',
                  color:      txnType === t.value ? '#fff' : 'var(--color-muted)',
                  border:     '1px solid var(--color-border)',
                }}>
                {t.label}
              </button>
            ))}
          </div>

          <div className="space-y-3">
            {!isNetworkTxn && (
              <>
                <SF label="Card Number (PAN)" value={form.cardNumber} onChange={e => setForm(p => ({ ...p, cardNumber: e.target.value }))} maxLength={19} placeholder="1234567890123456" />
                <SF label="Expiry Date (YYMM)" value={form.expiryDate} onChange={e => setForm(p => ({ ...p, expiryDate: e.target.value }))} maxLength={4} placeholder="2612" />
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Amount (minor units)</label>
                    <input type="number" min={0} value={form.amount}
                      onChange={e => setForm(p => ({ ...p, amount: parseFloat(e.target.value) || 0 }))}
                      className="w-full px-3 py-2 rounded-lg text-sm outline-none"
                      style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
                  </div>
                  <SF label="Currency (ISO numeric)" value={form.currency} onChange={e => setForm(p => ({ ...p, currency: e.target.value }))} maxLength={3} placeholder="840" />
                </div>

                {/* Entry mode */}
                <div>
                  <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>Entry Mode</label>
                  <div className="flex gap-2">
                    {ENTRY_MODES.map(m => (
                      <button key={m} onClick={() => setForm(p => ({ ...p, entryMode: m }))}
                        className="flex-1 py-2 rounded-lg text-xs font-medium transition-colors"
                        style={{
                          background: form.entryMode === m ? 'var(--color-primary)' : 'var(--bg-subtle)',
                          color:      form.entryMode === m ? '#fff' : 'var(--color-muted)',
                          border:     '1px solid var(--color-border)',
                        }}>
                        {m}
                      </button>
                    ))}
                  </div>
                </div>
              </>
            )}

            <SF label="Terminal ID" value={form.terminalId} onChange={e => setForm(p => ({ ...p, terminalId: e.target.value }))} maxLength={8} />
            {!isNetworkTxn && (
              <>
                <SF label="Merchant ID" value={form.merchantId} onChange={e => setForm(p => ({ ...p, merchantId: e.target.value }))} />
                <SF label="Merchant Name" value={form.merchantName} onChange={e => setForm(p => ({ ...p, merchantName: e.target.value }))} />
                {form.entryMode !== 'CONTACTLESS' && (
                  <SF label="PIN Block (ISO-0, hex — optional)" value={form.pinBlock ?? ''}
                    onChange={e => setForm(p => ({ ...p, pinBlock: e.target.value || undefined }))} />
                )}
              </>
            )}
          </div>

          <button onClick={send} disabled={simulate.isPending}
            className="w-full mt-5 py-2.5 rounded-lg text-sm font-semibold text-white disabled:opacity-60 transition-opacity"
            style={{ background: 'var(--color-primary)' }}>
            {simulate.isPending ? 'Sending…' : `Send ${TXN_TYPES.find(t => t.value === txnType)?.label}`}
          </button>

          {error && <p className="mt-3 text-xs p-3 rounded-lg" style={{ background: 'var(--color-error-bg)', color: 'var(--color-error)' }}>{error}</p>}
        </div>

        {/* Right — result */}
        <div className="rounded-xl p-5" style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
          <p className="text-sm font-semibold mb-4" style={{ color: 'var(--color-text)' }}>Response</p>

          {!result && !simulate.isPending && (
            <div className="flex items-center justify-center h-48 text-sm" style={{ color: 'var(--color-muted)' }}>
              Send a transaction to see the response
            </div>
          )}

          {simulate.isPending && (
            <div className="flex items-center justify-center h-48 text-sm" style={{ color: 'var(--color-muted)' }}>
              Waiting for FEP response…
            </div>
          )}

          {result && (
            <div className="space-y-4">
              {/* Approved / Declined banner */}
              <div className="rounded-lg p-4 flex items-center gap-3"
                style={{
                  background: isApproved ? 'var(--color-success-bg)' : 'var(--color-error-bg)',
                  border: `1px solid ${isApproved ? 'var(--color-success)' : 'var(--color-error)'}`,
                }}>
                <span className="text-2xl">{isApproved ? '✓' : '✗'}</span>
                <div>
                  <p className="text-sm font-bold" style={{ color: isApproved ? 'var(--color-success)' : 'var(--color-error)' }}>
                    {isApproved ? 'APPROVED' : 'DECLINED'}
                  </p>
                  <p className="text-xs" style={{ color: isApproved ? 'var(--color-success)' : 'var(--color-error)' }}>
                    RC {result.responseCode} — {RC_LABEL[result.responseCode] ?? result.responseDescription}
                  </p>
                </div>
              </div>

              {/* Fields */}
              <div className="grid grid-cols-2 gap-3 text-xs">
                {[
                  ['STAN', result.stan],
                  ['RRN',  result.rrn],
                  ['Auth Code', result.authCode ?? '—'],
                  ['Available Balance', result.availableBalance != null ? result.availableBalance.toLocaleString() : '—'],
                ].map(([k, v]) => (
                  <div key={String(k)}>
                    <p style={{ color: 'var(--color-muted)' }}>{k}</p>
                    <p className="font-mono font-medium" style={{ color: 'var(--color-text)' }}>{v}</p>
                  </div>
                ))}
              </div>

              {/* Hex dump (collapsible) */}
              {result.hexDump && (
                <div>
                  <button onClick={() => setHexOpen(o => !o)}
                    className="text-xs flex items-center gap-1" style={{ color: 'var(--color-muted)' }}>
                    <span>{hexOpen ? '▼' : '▶'}</span> ISO 8583 Hex Dump
                  </button>
                  {hexOpen && (
                    <pre className="mt-2 p-3 rounded-lg text-xs overflow-x-auto"
                      style={{ background: 'var(--bg-subtle)', color: 'var(--color-text)', fontFamily: 'monospace' }}>
                      {result.hexDump}
                    </pre>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function SF({ label, ...props }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="block text-xs font-medium mb-1" style={{ color: 'var(--color-muted)' }}>{label}</label>
      <input {...props} className="w-full px-3 py-2 rounded-lg text-sm outline-none font-mono"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
    </div>
  )
}
