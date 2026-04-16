// web-react/src/app/features/open-banking/api/types.ts

export type ConsentType   = 'AISP' | 'PISP' | 'CBPII'
export type ConsentStatus = 'AWAITING_AUTHORISATION' | 'AUTHORISED' | 'REVOKED'

export interface Consent {
  id: string
  type: ConsentType
  status: ConsentStatus
  scopes: string[]
  clientId: string
  tppName?: string
  redirectUri?: string
  expiresAt?: string
  authorisedAt?: string
  revokedAt?: string
  createdAt: string
  // PISP-specific
  debtorAccountId?: string
  creditorAccountId?: string
  amount?: number
  currency?: string
  reference?: string
  // CBPII-specific
  fundsAvailable?: boolean
}

export interface CreateConsentRequest {
  type: ConsentType
  scopes: string[]
  clientId: string
  redirectUri?: string
  expiresAt?: string
}
