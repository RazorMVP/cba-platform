// web-react/src/app/features/cards/SchemeConfigPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import { StatusBadge } from '@/shared/components/StatusBadge'

interface SchemeInfo {
  name: string
  fullName: string
  description: string
  privateDEs: string
  settlementFormat: string
  cryptogram: string
  status: 'ACTIVE' | 'STUB'
  activationKey: string
}

const SCHEMES: SchemeInfo[] = [
  {
    name: 'VISA',
    fullName: 'Visa International',
    description: 'VisaNet global card scheme. Uses BASE II settlement file format and Visa-specific DEs 60–63, 126.',
    privateDEs: 'DE 60–63, DE 126 (Visa-specific subelements)',
    settlementFormat: 'BASE II record format (SFTP to VisaNet)',
    cryptogram: '3DES CBC-MAC (EMV Book 2)',
    status: 'STUB',
    activationKey: 'visa',
  },
  {
    name: 'MASTERCARD',
    fullName: 'Mastercard Worldwide',
    description: 'Mastercard global card scheme. Uses IPM file format, DE 48 Private Data Subelements, and DE 111–127 for MIP.',
    privateDEs: 'DE 48 PDS (TAG 4 + LEN 3 + VALUE), DE 111–127 (MIP)',
    settlementFormat: 'IPM (ISO 8583 + private DEs, SFTP to GCMS)',
    cryptogram: '3DES CBC-MAC (EMV Book 2)',
    status: 'STUB',
    activationKey: 'mastercard',
  },
  {
    name: 'VERVE',
    fullName: 'Verve (Interswitch)',
    description: 'Nigeria domestic card scheme operated by Interswitch. Uses NIBSS e-settlement format.',
    privateDEs: 'DE 62–63 (Interswitch subelements)',
    settlementFormat: 'NIBSS e-settlement format (SFTP)',
    cryptogram: '3DES CBC-MAC (EMV Book 2)',
    status: 'STUB',
    activationKey: 'verve',
  },
  {
    name: 'AFRIGO',
    fullName: 'Afrigo (PAPSS)',
    description: 'Pan-African Payment and Settlement System card scheme. REST-based clearing via PAPSS API.',
    privateDEs: 'Minimal — largely standard ISO 8583',
    settlementFormat: 'PAPSS clearing format (HTTPS REST)',
    cryptogram: '3DES CBC-MAC (EMV Book 2)',
    status: 'STUB',
    activationKey: 'afrigo',
  },
  {
    name: 'UNION_PAY',
    fullName: 'China UnionPay (CUP)',
    description: 'China domestic and international card scheme. Uses QPBOC contactless profile and SM4 cryptography for domestic cards.',
    privateDEs: 'DE 60–63 (CUP subelements), DE 36/40/46–47/50; QPBOC tags 9F7C/9F77/9F78/9F79',
    settlementFormat: 'CUPS / CNAPS format (GB18030 encoding)',
    cryptogram: 'SM4 for domestic; 3DES fallback for international',
    status: 'STUB',
    activationKey: 'union_pay',
  },
]

function schemeVariant(name: string): 'info' | 'success' | 'warning' | 'neutral' | 'error' {
  if (name === 'VISA')       return 'info'
  if (name === 'MASTERCARD') return 'success'
  if (name === 'VERVE')      return 'warning'
  if (name === 'AFRIGO')     return 'neutral'
  return 'error'
}

export default function SchemeConfigPage() {
  const [open, setOpen] = useState<string | null>(null)
  const [copyLabel, setCopyLabel] = useState<string | null>(null)

  function copySnippet(key: string, text: string) {
    navigator.clipboard.writeText(text).then(() => {
      setCopyLabel(key)
      setTimeout(() => setCopyLabel(null), 2000)
    })
  }

  return (
    <div>
      <PageHeader
        title="Scheme Configuration"
        subtitle="Adapter status and activation guide for each supported card scheme"
      />

      <div className="rounded-xl mb-4 p-4 flex items-start gap-3"
        style={{ background: 'var(--color-warning-bg)', border: '1px solid var(--color-warning)' }}>
        <span style={{ color: 'var(--color-warning)' }}>⚠</span>
        <p className="text-xs" style={{ color: 'var(--color-warning)' }}>
          All scheme adapters are currently in STUB mode — they generate scheme-compliant file structures
          but do not transmit to live scheme networks. Set <code>enabled: true</code> and supply credentials
          in <code>application.yml</code> to activate a scheme.
        </p>
      </div>

      <div className="space-y-3">
        {SCHEMES.map(s => (
          <div key={s.name} className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
            {/* Header row */}
            <button
              onClick={() => setOpen(o => o === s.name ? null : s.name)}
              className="w-full flex items-center justify-between px-5 py-4"
              style={{ background: 'var(--bg-card)' }}>
              <div className="flex items-center gap-3">
                <StatusBadge label={s.name} variant={schemeVariant(s.name)} />
                <div className="text-left">
                  <p className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>{s.fullName}</p>
                  <p className="text-xs" style={{ color: 'var(--color-muted)' }}>{s.description}</p>
                </div>
              </div>
              <div className="flex items-center gap-3 shrink-0">
                <StatusBadge label={s.status} variant={s.status === 'ACTIVE' ? 'success' : 'neutral'} />
                <span className="text-xs" style={{ color: 'var(--color-muted)' }}>{open === s.name ? '▲' : '▼'}</span>
              </div>
            </button>

            {/* Expanded detail */}
            {open === s.name && (
              <div className="px-5 pb-5 pt-0" style={{ background: 'var(--bg-subtle)', borderTop: '1px solid var(--color-border)' }}>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 py-4">
                  {[
                    ['Private DEs',         s.privateDEs],
                    ['Settlement Format',   s.settlementFormat],
                    ['Cryptogram Algorithm',s.cryptogram],
                  ].map(([label, val]) => (
                    <div key={label}>
                      <p className="text-xs font-semibold mb-1" style={{ color: 'var(--color-muted)' }}>{label}</p>
                      <p className="text-xs" style={{ color: 'var(--color-text)' }}>{val}</p>
                    </div>
                  ))}
                </div>

                {/* Activation snippet */}
                <div>
                  <p className="text-xs font-semibold mb-2" style={{ color: 'var(--color-muted)' }}>
                    ACTIVATION SNIPPET — add to <code className="font-mono">application.yml</code>
                  </p>
                  <div className="relative">
                    <pre className="p-4 rounded-lg text-xs overflow-x-auto"
                      style={{ background: '#0a1628', color: '#a8d8a8', fontFamily: 'monospace' }}>
{`card:
  settlement:
    export:
      schemes:
        ${s.activationKey}:
          enabled: true
          sftp-host: \${${s.name}_SFTP_HOST}
          sftp-user: \${${s.name}_SFTP_USER}
          sftp-key-path: \${${s.name}_SFTP_KEY_PATH}
          remote-dir: /settlement/outbound`}
                    </pre>
                    <button
                      onClick={() => copySnippet(s.name, `card:\n  settlement:\n    export:\n      schemes:\n        ${s.activationKey}:\n          enabled: true\n          sftp-host: \${${s.name}_SFTP_HOST}\n          sftp-user: \${${s.name}_SFTP_USER}\n          sftp-key-path: \${${s.name}_SFTP_KEY_PATH}\n          remote-dir: /settlement/outbound`)}
                      className="absolute top-2 right-2 text-xs px-2 py-1 rounded"
                      style={{ background: 'rgba(255,255,255,0.1)', color: '#a8d8a8' }}>
                      {copyLabel === s.name ? 'Copied!' : 'Copy'}
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
