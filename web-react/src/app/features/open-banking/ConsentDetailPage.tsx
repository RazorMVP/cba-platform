// web-react/src/app/features/open-banking/ConsentDetailPage.tsx
import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'
import { useConsent, useAuthoriseConsent, useRevokeConsent } from './api/useOpenBanking'
import type { Consent, ConsentStatus, ConsentType } from './api/types'

// ── Helpers ───────────────────────────────────────────────────────────────────

const STATUS_VARIANT: Record<ConsentStatus, 'success' | 'warning' | 'neutral'> = {
  AUTHORISED:             'success',
  AWAITING_AUTHORISATION: 'warning',
  REVOKED:                'neutral',
}

const STATUS_LABEL: Record<ConsentStatus, string> = {
  AUTHORISED:             'Authorised',
  AWAITING_AUTHORISATION: 'Awaiting Authorisation',
  REVOKED:                'Revoked',
}

const TYPE_COLORS: Record<ConsentType, { bg: string; color: string }> = {
  AISP:  { bg: '#eff6ff', color: '#1d4ed8' },
  PISP:  { bg: '#f0fdf4', color: '#15803d' },
  CBPII: { bg: '#fef3c7', color: '#92400e' },
}

const TYPE_DESCRIPTIONS: Record<ConsentType, string> = {
  AISP:  'Account Information — read-only access to account and transaction data',
  PISP:  'Payment Initiation — permission to initiate a payment on behalf of the customer',
  CBPII: 'Funds Confirmation — ability to check whether funds are available',
}

// ── Confirm action modal ──────────────────────────────────────────────────────

function ConfirmModal({
  title,
  message,
  confirmLabel,
  danger,
  onConfirm,
  onClose,
  isPending,
}: {
  title: string
  message: string
  confirmLabel: string
  danger?: boolean
  onConfirm: () => void
  onClose: () => void
  isPending: boolean
}) {
  return (
    <div
      style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 50, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="rounded-xl p-6 w-full max-w-sm"
        style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <h2 className="text-base font-semibold mb-2" style={{ color: 'var(--color-text)' }}>{title}</h2>
        <p className="text-sm mb-5" style={{ color: 'var(--color-muted)' }}>{message}</p>
        <div className="flex gap-2 justify-end">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
            Cancel
          </button>
          <button
            onClick={onConfirm}
            disabled={isPending}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: danger ? 'var(--color-error)' : 'var(--color-success)' }}
          >
            {isPending ? '…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Detail field ──────────────────────────────────────────────────────────────

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-semibold uppercase tracking-wider mb-1"
        style={{ color: 'var(--color-muted)' }}>
        {label}
      </dt>
      <dd className="text-sm" style={{ color: 'var(--color-text)' }}>
        {value ?? <span style={{ color: 'var(--color-muted)' }}>—</span>}
      </dd>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function ConsentDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const { data: raw, isLoading } = useConsent(id!)
  const consent: Consent | undefined = (raw as { data?: Consent } | undefined)?.data

  const authorise = useAuthoriseConsent(id!)
  const revoke    = useRevokeConsent(id!)

  const [modal, setModal] = useState<'authorise' | 'revoke' | null>(null)

  if (isLoading || !consent) return (
    <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
  )

  const typeStyle = TYPE_COLORS[consent.type]

  async function handleAuthorise() {
    await authorise.mutateAsync()
    setModal(null)
  }

  async function handleRevoke() {
    await revoke.mutateAsync()
    setModal(null)
    navigate('/open-banking/consents')
  }

  return (
    <div>
      <PageHeader
        title={`Consent ${consent.id.slice(0, 8)}…`}
        subtitle={TYPE_DESCRIPTIONS[consent.type]}
        actions={
          <div className="flex gap-2">
            {consent.status === 'AWAITING_AUTHORISATION' && (
              <button
                onClick={() => setModal('authorise')}
                className="text-xs px-3 py-1.5 rounded-lg text-white"
                style={{ background: 'var(--color-success)' }}
              >
                Authorise
              </button>
            )}
            {consent.status !== 'REVOKED' && (
              <button
                onClick={() => setModal('revoke')}
                className="text-xs px-3 py-1.5 rounded-lg"
                style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}
              >
                Revoke
              </button>
            )}
          </div>
        }
      />

      {/* Status + type banner */}
      <div className="flex items-center gap-3 mb-6">
        <StatusBadge
          label={STATUS_LABEL[consent.status]}
          variant={STATUS_VARIANT[consent.status]}
        />
        <span className="text-xs font-semibold px-2 py-0.5 rounded-full"
          style={{ background: typeStyle.bg, color: typeStyle.color }}>
          {consent.type}
        </span>
      </div>

      {/* Two-column detail grid */}
      <div className="rounded-xl p-6 mb-6"
        style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <dl className="grid grid-cols-2 gap-x-8 gap-y-5">
          <Field label="Consent ID"    value={<span className="font-mono text-xs">{consent.id}</span>} />
          <Field label="Type"          value={consent.type} />
          <Field label="Status"        value={STATUS_LABEL[consent.status]} />
          <Field label="Client ID"     value={<span className="font-mono text-xs">{consent.clientId}</span>} />
          {consent.tppName && (
            <Field label="TPP Name"    value={consent.tppName} />
          )}
          {consent.redirectUri && (
            <Field label="Redirect URI" value={<span className="font-mono text-xs break-all">{consent.redirectUri}</span>} />
          )}
          <Field label="Created"       value={new Date(consent.createdAt).toLocaleString()} />
          {consent.authorisedAt && (
            <Field label="Authorised"  value={new Date(consent.authorisedAt).toLocaleString()} />
          )}
          {consent.expiresAt && (
            <Field label="Expires"     value={new Date(consent.expiresAt).toLocaleString()} />
          )}
          {consent.revokedAt && (
            <Field label="Revoked"     value={new Date(consent.revokedAt).toLocaleString()} />
          )}

          {/* PISP fields */}
          {consent.type === 'PISP' && (
            <>
              {consent.amount !== undefined && (
                <Field label="Amount"
                  value={<span className="tabular-nums">{consent.amount} {consent.currency}</span>} />
              )}
              {consent.reference && (
                <Field label="Reference" value={consent.reference} />
              )}
              {consent.debtorAccountId && (
                <Field label="Debtor Account"   value={<span className="font-mono text-xs">{consent.debtorAccountId}</span>} />
              )}
              {consent.creditorAccountId && (
                <Field label="Creditor Account" value={<span className="font-mono text-xs">{consent.creditorAccountId}</span>} />
              )}
            </>
          )}

          {/* CBPII funds confirmation result */}
          {consent.type === 'CBPII' && consent.fundsAvailable !== undefined && (
            <Field label="Funds Available"
              value={
                <StatusBadge
                  label={consent.fundsAvailable ? 'Yes' : 'No'}
                  variant={consent.fundsAvailable ? 'success' : 'error'}
                />
              }
            />
          )}
        </dl>
      </div>

      {/* Scopes */}
      <div className="rounded-xl p-6"
        style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <h3 className="text-xs font-semibold uppercase tracking-wider mb-3"
          style={{ color: 'var(--color-muted)' }}>
          Granted Scopes
        </h3>
        <div className="flex flex-wrap gap-2">
          {consent.scopes.map(s => (
            <span key={s} className="text-xs font-mono px-2.5 py-1 rounded-full"
              style={{ background: 'var(--bg-subtle)', color: 'var(--color-text)', border: '1px solid var(--color-border)' }}>
              {s}
            </span>
          ))}
        </div>
      </div>

      {/* Confirm modals */}
      {modal === 'authorise' && (
        <ConfirmModal
          title="Authorise Consent"
          message="This will move the consent from Awaiting Authorisation to Authorised, granting the TPP access to the requested scopes."
          confirmLabel="Authorise"
          onConfirm={handleAuthorise}
          onClose={() => setModal(null)}
          isPending={authorise.isPending}
        />
      )}

      {modal === 'revoke' && (
        <ConfirmModal
          title="Revoke Consent"
          message="Revoking this consent immediately terminates the TPP's access. This cannot be undone — the TPP must obtain a new consent."
          confirmLabel="Revoke"
          danger
          onConfirm={handleRevoke}
          onClose={() => setModal(null)}
          isPending={revoke.isPending}
        />
      )}
    </div>
  )
}
