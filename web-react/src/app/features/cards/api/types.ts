// web-react/src/app/features/cards/api/types.ts

export type CardType     = 'DEBIT' | 'PREPAID' | 'CREDIT'
export type CardStatus   = 'ORDERED' | 'PRODUCED' | 'DISPATCHED' | 'ACTIVATION_PENDING' | 'ACTIVE' | 'BLOCKED' | 'EXPIRED' | 'CANCELLED' | 'ISSUED'
export type SchemeType   = 'VISA' | 'MASTERCARD' | 'VERVE' | 'AFRIGO' | 'UNION_PAY'
export type EntryMode    = 'CHIP' | 'SWIPE' | 'CONTACTLESS'
export type BatchStatus  = 'OPEN' | 'CLOSED' | 'SETTLED' | 'FAILED'
export type TransmissionStatus = 'PENDING' | 'TRANSMITTED' | 'ACKNOWLEDGED' | 'FAILED'
export type DisputeStatus  = 'RAISED' | 'RETRIEVAL_REQUESTED' | 'CHARGEBACK_INITIATED' | 'REPRESENTMENT' | 'PRE_ARBITRATION' | 'RESOLVED' | 'WITHDRAWN'
export type DisputeReason  = 'UNAUTHORIZED' | 'GOODS_NOT_RECEIVED' | 'DUPLICATE' | 'AMOUNT_MISMATCH' | 'OTHER'
export type TxnType = 'PURCHASE' | 'CASH' | 'REFUND'
export type Channel = 'CARD_PRESENT' | 'CNP'
export type FeeType = 'ASSESSMENT' | 'NETWORK' | 'CROSS_BORDER' | 'INTERNATIONAL_SERVICE'

// ── Entities ──────────────────────────────────────────────────────────────────

export interface Card {
  id: string
  panLast4: string
  panPrefix: string
  cardType: CardType
  status: CardStatus
  virtualFlag: boolean
  customerId: string
  customerName?: string
  linkedEntityId?: string
  productId: string
  productName?: string
  expiryDate: string   // YYMM
  pinRetryCount: number
  createdAt: string
}

export interface CardLimit {
  id: string
  cardId: string
  dailyPurchaseLimit: number
  dailyWithdrawalLimit: number
  perTxnLimit: number
  monthlyLimit: number
  currencyCode: string
}

export interface CardProduct {
  id: string
  name: string
  cardType: CardType
  binRangeStart: string
  binRangeEnd: string
  defaultDailyLimit: number
  features: Record<string, unknown>
  active: boolean
  createdAt: string
}

export interface FraudRule {
  id: string
  ruleId: string
  weight: number
  enabled: boolean
  params: Record<string, unknown>
}

export interface AuthorizationLog {
  id: string
  cardId: string
  stan: string
  rrn: string
  mti: string
  processingCode: string
  amount: number
  currencyCode: string
  responseCode: string
  entryMode: string
  merchantId: string
  merchantName: string
  mcc: string
  fraudScore: number
  decision: 'APPROVE' | 'STEP_UP' | 'DECLINE'
  createdAt: string
}

export interface SettlementBatch {
  id: string
  batchRef: string
  status: BatchStatus
  settlementDate: string
  totalAmount: number
  itemCount: number
  openedAt: string
  closedAt?: string
}

export interface SettlementTransmission {
  id: string
  batchId: string
  scheme: SchemeType
  status: TransmissionStatus
  attemptCount: number
  lastAttemptAt?: string
  endpoint?: string
}

export interface CardDispute {
  id: string
  cardId: string
  transactionRef: string
  disputeReason: DisputeReason
  status: DisputeStatus
  raisedBy: string
  resolvedBy?: string
  originalAmount: number
  resolutionNotes?: string
  resolutionFavor?: string
  chargebackDeadline?: string
  responseDeadline?: string
  createdAt: string
}

export interface ChargebackReasonCode {
  id: string
  scheme: SchemeType
  code: string
  description: string
  maxDaysToRespond: number
}

export interface ApiKey {
  id: string
  name: string
  scopes: string[]
  active: boolean
  lastUsedAt?: string
  createdAt: string
  keyValue?: string   // only present on create response
}

export interface Webhook {
  id: string
  name: string
  callbackUrl: string
  events: string[]
  active: boolean
  createdAt: string
}

export interface WebhookDelivery {
  id: string
  webhookId: string
  eventType: string
  deliveryUuid: string
  httpStatus?: number
  status: 'PENDING' | 'DELIVERED' | 'FAILED'
  attemptCount: number
  lastAttemptAt?: string
}

export interface BinRange {
  id: string
  binStart: string
  binEnd: string
  scheme: SchemeType
  productType?: string
  cardType: CardType
  countryCode?: string
  currencyCode?: string
  active: boolean
}

export interface InterchangeRate {
  id: string
  scheme: SchemeType
  cardType: CardType
  mccCategory?: string
  transactionType: TxnType
  channel: Channel
  ratePercent: number
  fixedFee: number
  currencyCode: string
  effectiveFrom: string
  effectiveTo?: string
  active: boolean
}

export interface SchemeFee {
  id: string
  scheme: SchemeType
  feeType: FeeType
  ratePercent: number
  fixedFee: number
  effectiveFrom: string
  active: boolean
}

// ── Simulator ────────────────────────────────────────────────────────────────

export interface SimulateRequest {
  cardNumber: string
  expiryDate: string
  amount: number
  currency: string
  terminalId: string
  merchantId: string
  merchantName: string
  entryMode: EntryMode
  pinBlock?: string
}

export interface SimulateResponse {
  responseCode: string
  responseDescription: string
  authCode?: string
  availableBalance?: number
  stan: string
  rrn: string
  approved: boolean
  hexDump?: string
}

// ── Request DTOs ─────────────────────────────────────────────────────────────

export interface CardIssueRequest {
  customerId: string
  productId: string
  cardType: CardType
  virtualFlag: boolean
  linkedEntityId?: string
}

export interface CardLimitRequest {
  dailyPurchaseLimit: number
  dailyWithdrawalLimit: number
  perTxnLimit: number
  monthlyLimit: number
  currencyCode: string
}

export interface CardProductRequest {
  name: string
  cardType: CardType
  binRangeStart: string
  binRangeEnd: string
  defaultDailyLimit: number
}

export interface ApiKeyRequest {
  name: string
  scopes: string[]
}

export interface WebhookRequest {
  name: string
  callbackUrl: string
  events: string[]
  secret: string
}

export interface BinRangeRequest {
  binStart: string
  binEnd: string
  scheme: SchemeType
  productType?: string
  cardType: CardType
  countryCode?: string
  currencyCode?: string
}

export interface InterchangeRateRequest {
  scheme: SchemeType
  cardType: CardType
  mccCategory?: string
  transactionType: TxnType
  channel: Channel
  ratePercent: number
  fixedFee: number
  currencyCode: string
  effectiveFrom: string
  effectiveTo?: string
}

export interface SchemeFeeRequest {
  scheme: SchemeType
  feeType: FeeType
  ratePercent: number
  fixedFee: number
  effectiveFrom: string
}

export interface DisputeRequest {
  cardId: string
  transactionRef: string
  disputeReason: DisputeReason
  originalAmount: number
}

export interface ResolveDisputeRequest {
  resolvedBy: string
  resolutionFavor: string
  resolutionNotes?: string
}
