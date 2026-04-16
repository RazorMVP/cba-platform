// web-react/src/app/features/system/AccountAlgorithmsPage.tsx
import { useState, useEffect } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { useTenantAlgorithm, useUpdateTenantAlgorithm } from './api/useSystem'
import type { AlgorithmType, ValidationMode, UpdateAlgorithmRequest } from './api/types'

// ── The tenant ID to manage — in a real deployment this would come from
//    a tenant picker or the current session context. We use the demo tenant.
const DEMO_TENANT_ID = 'CBA_NG'

const ACCOUNT_TYPES = ['SAVINGS', 'CHECKING', 'LOAN', 'CLIENT', 'SHARE'] as const

const ALGORITHM_OPTIONS: { value: AlgorithmType; label: string; description: string }[] = [
  { value: 'MIFOS',  label: 'MIFOS',  description: 'Branch-type-sequence format (e.g. 001-SAV-0001234)' },
  { value: 'NUBAN',  label: 'NUBAN',  description: '10-digit NUBAN format with check digit (Nigeria CBN standard)' },
]

const VALIDATION_MODES: { value: ValidationMode; label: string; description: string }[] = [
  { value: 'STRICT',   label: 'Strict',   description: 'Check digit validation only (inter-bank)' },
  { value: 'PARANOID', label: 'Paranoid', description: 'Check digit + own bank code verification (intra-bank)' },
]

// ── Algorithm toggle per account type ─────────────────────────────────────────

function AlgorithmToggle({
  accountType,
  current,
  onChange,
}: {
  accountType: string
  current: AlgorithmType
  onChange: (v: AlgorithmType) => void
}) {
  return (
    <div className="flex items-center justify-between px-4 py-3"
      style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
      <span className="text-sm font-mono font-medium" style={{ color: 'var(--color-text)' }}>
        {accountType}
      </span>
      <div className="flex gap-1 p-0.5 rounded-lg" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)' }}>
        {ALGORITHM_OPTIONS.map(opt => (
          <button
            key={opt.value}
            onClick={() => onChange(opt.value)}
            title={opt.description}
            className="text-xs px-3 py-1 rounded-md transition-colors"
            style={{
              background: current === opt.value ? 'var(--bg-card)' : 'transparent',
              color:      current === opt.value ? 'var(--color-text)' : 'var(--color-muted)',
              fontWeight: current === opt.value ? 600 : 400,
              border:     current === opt.value ? '1px solid var(--color-border)' : '1px solid transparent',
            }}
          >
            {opt.label}
          </button>
        ))}
      </div>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function AccountAlgorithmsPage() {
  const { data, isLoading } = useTenantAlgorithm(DEMO_TENANT_ID)
  const update = useUpdateTenantAlgorithm(DEMO_TENANT_ID)

  const config = (data as { data?: { bankCode?: string; validationMode: ValidationMode; algorithms: Record<string, AlgorithmType> } } | undefined)?.data

  const [bankCode,        setBankCode]        = useState('')
  const [validationMode,  setValidationMode]  = useState<ValidationMode>('STRICT')
  const [algorithms,      setAlgorithms]      = useState<Record<string, AlgorithmType>>({})
  const [dirty,           setDirty]           = useState(false)

  // Seed form from server data
  useEffect(() => {
    if (!config) return
    setBankCode(config.bankCode ?? '')
    setValidationMode(config.validationMode)
    const base: Record<string, AlgorithmType> = {}
    ACCOUNT_TYPES.forEach(t => { base[t] = config.algorithms?.[t] ?? 'MIFOS' })
    setAlgorithms(base)
    setDirty(false)
  }, [config])

  function setAlgorithm(accountType: string, value: AlgorithmType) {
    setAlgorithms(prev => ({ ...prev, [accountType]: value }))
    setDirty(true)
  }

  function handleValidationMode(mode: ValidationMode) {
    setValidationMode(mode)
    setDirty(true)
  }

  function handleBankCode(code: string) {
    setBankCode(code)
    setDirty(true)
  }

  async function save() {
    const body: UpdateAlgorithmRequest = { bankCode: bankCode || undefined, validationMode, algorithms }
    await update.mutateAsync(body)
    setDirty(false)
  }

  const nubanInUse = Object.values(algorithms).some(v => v === 'NUBAN')

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader
        title="Account Number Algorithms"
        subtitle="Per-account-type number generation strategy and validation rules"
        actions={
          <button
            onClick={save}
            disabled={!dirty || update.isPending}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}
          >
            {update.isPending ? 'Saving…' : 'Save Changes'}
          </button>
        }
      />

      <div className="grid gap-6 max-w-2xl">

        {/* Tenant context */}
        <div className="rounded-xl px-4 py-3 flex items-center gap-3"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)' }}>
          <span className="text-xs" style={{ color: 'var(--color-muted)' }}>Tenant</span>
          <span className="text-xs font-mono font-semibold" style={{ color: 'var(--color-text)' }}>
            {DEMO_TENANT_ID}
          </span>
        </div>

        {/* Per-account-type algorithm toggles */}
        <div>
          <h3 className="text-xs font-semibold uppercase tracking-wider mb-3"
            style={{ color: 'var(--color-muted)' }}>
            Algorithm per Account Type
          </h3>
          <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
            {ACCOUNT_TYPES.map(type => (
              <AlgorithmToggle
                key={type}
                accountType={type}
                current={algorithms[type] ?? 'MIFOS'}
                onChange={v => setAlgorithm(type, v)}
              />
            ))}
          </div>
          <p className="mt-2 text-xs" style={{ color: 'var(--color-muted)' }}>
            Hover an option to see its format description. Changing the algorithm affects newly created accounts only.
          </p>
        </div>

        {/* NUBAN-specific settings — only visible when at least one type uses NUBAN */}
        {nubanInUse && (
          <div>
            <h3 className="text-xs font-semibold uppercase tracking-wider mb-3"
              style={{ color: 'var(--color-muted)' }}>
              NUBAN Settings
            </h3>
            <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>

              {/* Bank code */}
              <div className="px-4 py-4" style={{ background: 'var(--bg-card)', borderBottom: '1px solid var(--color-border)' }}>
                <label className="block text-xs font-medium mb-2" style={{ color: 'var(--color-text)' }}>
                  CBN Bank Code
                </label>
                <div className="flex items-center gap-3">
                  <input
                    type="text"
                    value={bankCode}
                    onChange={e => handleBankCode(e.target.value.replace(/\D/g, '').slice(0, 3))}
                    placeholder="3-digit code (e.g. 058)"
                    maxLength={3}
                    className="px-3 py-2 rounded-lg text-sm font-mono outline-none w-36"
                    style={{
                      background: 'var(--bg-subtle)',
                      border: '1px solid var(--color-border)',
                      color: 'var(--color-text)',
                    }}
                  />
                  <span className="text-xs" style={{ color: 'var(--color-muted)' }}>
                    Assigned by the Central Bank of Nigeria
                  </span>
                </div>
              </div>

              {/* Validation mode */}
              <div className="px-4 py-4" style={{ background: 'var(--bg-card)' }}>
                <label className="block text-xs font-medium mb-2" style={{ color: 'var(--color-text)' }}>
                  Validation Mode
                </label>
                <div className="space-y-2">
                  {VALIDATION_MODES.map(mode => (
                    <label key={mode.value}
                      className="flex items-start gap-3 cursor-pointer p-3 rounded-lg"
                      style={{
                        background: validationMode === mode.value ? 'var(--bg-subtle)' : 'transparent',
                        border: validationMode === mode.value ? '1px solid var(--color-border)' : '1px solid transparent',
                      }}>
                      <input
                        type="radio"
                        name="validationMode"
                        value={mode.value}
                        checked={validationMode === mode.value}
                        onChange={() => handleValidationMode(mode.value)}
                        className="mt-0.5"
                      />
                      <div>
                        <span className="text-sm font-medium block" style={{ color: 'var(--color-text)' }}>
                          {mode.label}
                        </span>
                        <span className="text-xs" style={{ color: 'var(--color-muted)' }}>
                          {mode.description}
                        </span>
                      </div>
                    </label>
                  ))}
                </div>
              </div>
            </div>

            {/* NUBAN algorithm info */}
            <div className="mt-3 rounded-lg px-4 py-3"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)' }}>
              <p className="text-xs" style={{ color: 'var(--color-muted)' }}>
                <strong style={{ color: 'var(--color-text)' }}>NUBAN format:</strong>{' '}
                10 digits = Bank Code (3) + Serial (6) + Check Digit (1).
                Check digit: weights {'{3,7,3,3,7,3,3,7,3}'}, formula{' '}
                <code className="font-mono">(10 − (Σ digit×weight % 10)) % 10</code>.
              </p>
            </div>
          </div>
        )}

        {/* Unsaved changes indicator */}
        {dirty && (
          <div className="rounded-lg px-4 py-2.5 flex items-center justify-between"
            style={{ background: '#fef3c7', border: '1px solid #f59e0b' }}>
            <span className="text-xs" style={{ color: '#92400e' }}>You have unsaved changes.</span>
            <button onClick={save} disabled={update.isPending}
              className="text-xs px-3 py-1 rounded-lg text-white disabled:opacity-60"
              style={{ background: 'var(--color-primary)' }}>
              {update.isPending ? 'Saving…' : 'Save Now'}
            </button>
          </div>
        )}

      </div>
    </div>
  )
}
