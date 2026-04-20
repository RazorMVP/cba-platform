import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/models/api-response.model';

// ── Shared ────────────────────────────────────────────────────────────────────
export type SchemeType = 'VISA' | 'MASTERCARD' | 'VERVE' | 'AFRIGO' | 'UNIONPAY' | 'UNKNOWN';
export type CardType   = 'DEBIT' | 'PREPAID' | 'CREDIT';
export type CardStatus = 'ORDERED' | 'PRODUCED' | 'DISPATCHED' | 'ACTIVATION_PENDING' |
                         'ACTIVE' | 'BLOCKED' | 'EXPIRED' | 'CANCELLED';

// ── Card Products ─────────────────────────────────────────────────────────────
export interface CardProduct {
  id: string;
  name: string;
  cardType: CardType;
  binRangeStart: string;
  binRangeEnd: string;
  defaultDailyLimit: number;
  features: Record<string, unknown>;
}

export interface CardProductRequest {
  name: string;
  cardType: CardType;
  binRangeStart: string;
  binRangeEnd: string;
  defaultDailyLimit: number;
}

// ── Cards ─────────────────────────────────────────────────────────────────────
export interface Card {
  id: string;
  panPrefix: string;
  panSuffix: string;
  expiryDate: string;
  cardType: CardType;
  status: CardStatus;
  virtualFlag: boolean;
  customerId: string;
  linkedEntityId: string;
  productId: string;
  productName: string;
  pinRetryCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface IssueCardRequest {
  customerId: string;
  productId: string;
  linkedEntityId: string;
  virtual: boolean;
}

export interface CardLimit {
  id: string;
  cardId: string;
  dailyPurchaseLimit: number;
  dailyWithdrawalLimit: number;
  perTxnLimit: number;
  monthlyLimit: number;
  currencyCode: string;
}

export interface UpdateLimitsRequest {
  dailyPurchaseLimit: number;
  dailyWithdrawalLimit: number;
  perTxnLimit: number;
  monthlyLimit: number;
}

export interface CardBalance {
  availableBalance: number;
  cardType: CardType;
}

// ── Fraud Rules ───────────────────────────────────────────────────────────────
export interface FraudRule {
  id: string;
  ruleId: string;
  weight: number;
  enabled: boolean;
  params: Record<string, unknown>;
}

export interface UpdateFraudRuleRequest {
  weight: number;
  enabled: boolean;
  params: Record<string, unknown>;
}

// ── Authorization Log ─────────────────────────────────────────────────────────
export interface AuthorizationLog {
  id: string;
  cardId: string;
  stan: string;
  rrn: string;
  mti: string;
  processingCode: string;
  amount: number;
  currencyCode: string;
  responseCode: string;
  entryMode: string;
  merchantId: string;
  merchantName: string;
  mcc: string;
  fraudScore: number;
  decision: string;
  createdAt: string;
}

// ── Settlement ────────────────────────────────────────────────────────────────
export type BatchStatus = 'OPEN' | 'CLOSED' | 'SETTLED' | 'FAILED';

export interface SettlementBatch {
  id: string;
  batchRef: string;
  status: BatchStatus;
  settlementDate: string;
  totalAmount: number;
  itemCount: number;
  openedAt: string;
  closedAt: string | null;
}

export interface SettlementTransmission {
  id: string;
  batchId: string;
  scheme: SchemeType;
  status: string;
  endpoint: string;
  attemptCount: number;
  lastAttemptAt: string | null;
  transmittedAt: string | null;
}

// ── Disputes ──────────────────────────────────────────────────────────────────
export type DisputeStatus = 'RAISED' | 'RETRIEVAL_REQUESTED' | 'CHARGEBACK_INITIATED' |
                            'REPRESENTMENT' | 'PRE_ARBITRATION' | 'RESOLVED' | 'WITHDRAWN';
export type DisputeReason = 'UNAUTHORIZED' | 'GOODS_NOT_RECEIVED' | 'DUPLICATE' |
                            'AMOUNT_MISMATCH' | 'OTHER';

export interface CardDispute {
  id: string;
  cardId: string;
  transactionRef: string;
  disputeReason: DisputeReason;
  status: DisputeStatus;
  raisedBy: string;
  resolvedBy: string | null;
  originalAmount: number;
  resolutionNotes: string | null;
  schemeReasonCode: string | null;
  chargebackDeadline: string | null;
  responseDeadline: string | null;
  createdAt: string;
}

export interface RaiseDisputeRequest {
  cardId: string;
  transactionRef: string;
  disputeReason: DisputeReason;
  raisedBy: string;
  originalAmount: number;
  currencyCode: string;
}

export interface ResolveDisputeRequest {
  resolvedBy: string;
  resolutionFavor: 'ISSUER' | 'ACQUIRER';
  notes: string;
}

export interface ChargebackReasonCode {
  id: string;
  scheme: SchemeType;
  code: string;
  description: string;
  maxDaysToRespond: number;
}

// ── Terminal Simulator ────────────────────────────────────────────────────────
export type EntryMode = 'SWIPE' | 'CHIP' | 'CONTACTLESS';

export interface SimulateRequest {
  cardNumber: string;
  expiryDate: string;
  amount: number;
  currency: string;
  terminalId: string;
  merchantId: string;
  merchantName: string;
  entryMode: EntryMode;
  pinBlock?: string;
}

export interface SimulateResponse {
  responseCode: string;
  responseDescription: string;
  authCode: string;
  availableBalance: number | null;
  stan: string;
  rrn: string;
  hexDump: string;
}

// ── BIN Management ────────────────────────────────────────────────────────────
export interface BinRange {
  id: string;
  binStart: string;
  binEnd: string;
  scheme: SchemeType;
  productType: string;
  cardType: CardType;
  countryCode: string;
  currencyCode: string;
  active: boolean;
}

export interface BinRangeRequest {
  binStart: string;
  binEnd: string;
  scheme: SchemeType;
  productType: string;
  cardType: CardType;
  countryCode: string;
  currencyCode: string;
}

// ── Interchange ───────────────────────────────────────────────────────────────
export type TransactionType = 'PURCHASE' | 'CASH' | 'REFUND';
export type ChannelType     = 'CARD_PRESENT' | 'CNP';

export interface InterchangeRate {
  id: string;
  scheme: SchemeType;
  cardType: CardType;
  mccCategory: string | null;
  transactionType: TransactionType;
  channel: ChannelType;
  ratePercent: number;
  fixedFee: number;
  currencyCode: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  active: boolean;
}

export interface InterchangeRateRequest {
  scheme: SchemeType;
  cardType: CardType;
  mccCategory?: string;
  transactionType: TransactionType;
  channel: ChannelType;
  ratePercent: number;
  fixedFee: number;
  currencyCode: string;
  effectiveFrom: string;
  effectiveTo?: string;
}

export interface SchemeFee {
  id: string;
  scheme: SchemeType;
  feeType: string;
  ratePercent: number;
  fixedFee: number;
  effectiveFrom: string;
  active: boolean;
}

// ── API Keys ──────────────────────────────────────────────────────────────────
export interface ApiKey {
  id: string;
  name: string;
  keyHash: string;
  active: boolean;
  scopes: string[];
  tier: 'SANDBOX' | 'BASIC' | 'PRO' | 'ENTERPRISE';
  lastUsedAt: string | null;
  createdAt: string;
}

export interface IssueApiKeyRequest {
  name: string;
  scopes: string[];
  tier: 'SANDBOX' | 'BASIC' | 'PRO' | 'ENTERPRISE';
}

export interface IssueApiKeyResponse {
  id: string;
  name: string;
  key: string;       // plaintext — shown once only
  scopes: string[];
  createdAt: string;
}

// ── Webhooks ──────────────────────────────────────────────────────────────────
export interface Webhook {
  id: string;
  name: string;
  callbackUrl: string;
  events: string[];
  active: boolean;
  createdAt: string;
}

export interface RegisterWebhookRequest {
  name: string;
  callbackUrl: string;
  events: string[];
  secret: string;
}

export interface WebhookDelivery {
  id: string;
  webhookId: string;
  eventType: string;
  deliveryUuid: string;
  httpStatus: number | null;
  status: 'PENDING' | 'DELIVERED' | 'FAILED';
  attemptCount: number;
  lastAttemptAt: string | null;
  nextRetryAt: string | null;
}

// ── Service ───────────────────────────────────────────────────────────────────
@Injectable({ providedIn: 'root' })
export class CardsService {
  private readonly http = inject(HttpClient);
  private readonly base   = `${environment.cardServiceUrl}/api/v1`;
  private readonly cardApi = `${environment.cardServiceUrl}/card-api/v1`;

  private get<T>(path: string, params?: Record<string, string>): Observable<T> {
    let p = new HttpParams();
    if (params) Object.entries(params).forEach(([k, v]) => p = p.set(k, v));
    return this.http.get<ApiResponse<T>>(`${this.base}${path}`, { params: p })
      .pipe(map(r => r.data));
  }

  private getCardApi<T>(path: string): Observable<T> {
    return this.http.get<ApiResponse<T>>(`${this.cardApi}${path}`)
      .pipe(map(r => r.data));
  }

  private post<T>(path: string, body: unknown, cardApi = false): Observable<T> {
    const base = cardApi ? this.cardApi : this.base;
    return this.http.post<ApiResponse<T>>(`${base}${path}`, body)
      .pipe(map(r => r.data));
  }

  private put<T>(path: string, body: unknown, cardApi = false): Observable<T> {
    const base = cardApi ? this.cardApi : this.base;
    return this.http.put<ApiResponse<T>>(`${base}${path}`, body)
      .pipe(map(r => r.data));
  }

  private del<T>(path: string, cardApi = false): Observable<T> {
    const base = cardApi ? this.cardApi : this.base;
    return this.http.delete<ApiResponse<T>>(`${base}${path}`)
      .pipe(map(r => r.data));
  }

  private cmd<T>(path: string, command: string, body: unknown = {}): Observable<T> {
    return this.http.post<ApiResponse<T>>(`${this.base}${path}?command=${command}`, body)
      .pipe(map(r => r.data));
  }

  // ── Card Products ──────────────────────────────────────────────────────────
  listProducts(): Observable<CardProduct[]> { return this.get<CardProduct[]>('/cards/products'); }
  createProduct(req: CardProductRequest): Observable<CardProduct> { return this.post<CardProduct>('/cards/products', req); }

  // ── Cards ──────────────────────────────────────────────────────────────────
  listCards(params?: Record<string, string>): Observable<Card[]> {
    let p = new HttpParams();
    if (params) Object.entries(params).forEach(([k, v]) => p = p.set(k, v));
    return this.http.get<ApiResponse<Card[]>>(`${this.cardApi}/cards`, { params: p }).pipe(map(r => r.data));
  }
  getCard(id: string): Observable<Card> { return this.get<Card>(`/cards/${id}`); }
  issueCard(req: IssueCardRequest): Observable<Card> { return this.post<Card>('/cards', req); }
  commandCard(id: string, command: string): Observable<Card> { return this.cmd<Card>(`/cards/${id}`, command); }
  getCardBalance(id: string): Observable<CardBalance> { return this.get<CardBalance>(`/cards/${id}/balance`); }
  getCardLimits(id: string): Observable<CardLimit> {
    return this.http.get<ApiResponse<CardLimit>>(`${this.cardApi}/cards/${id}/limits`).pipe(map(r => r.data));
  }
  updateCardLimits(id: string, req: UpdateLimitsRequest): Observable<CardLimit> {
    return this.http.put<ApiResponse<CardLimit>>(`${this.cardApi}/cards/${id}/limits`, req).pipe(map(r => r.data));
  }
  listAuthorizations(id: string): Observable<AuthorizationLog[]> {
    return this.http.get<ApiResponse<AuthorizationLog[]>>(`${this.cardApi}/cards/${id}/authorizations`).pipe(map(r => r.data));
  }

  // ── Fraud Rules ────────────────────────────────────────────────────────────
  listFraudRules(): Observable<FraudRule[]> { return this.get<FraudRule[]>('/cards/fraud/rules'); }
  updateFraudRule(id: string, req: UpdateFraudRuleRequest): Observable<FraudRule> {
    return this.put<FraudRule>(`/cards/fraud/rules/${id}`, req);
  }

  // ── Settlement ─────────────────────────────────────────────────────────────
  listBatches(): Observable<SettlementBatch[]> { return this.get<SettlementBatch[]>('/cards/settlement/batches'); }
  closeBatch(id: string): Observable<SettlementBatch> { return this.post<SettlementBatch>(`/cards/settlement/batches/${id}/close`, {}); }
  triggerExport(batchId: string): Observable<void> { return this.post<void>(`/cards/settlement/export/${batchId}`, {}); }
  listTransmissions(): Observable<SettlementTransmission[]> { return this.get<SettlementTransmission[]>('/cards/settlement/transmissions'); }

  // ── Disputes ───────────────────────────────────────────────────────────────
  listDisputes(status?: string): Observable<CardDispute[]> {
    return this.get<CardDispute[]>('/cards/disputes', status ? { status } : undefined);
  }
  raiseDispute(req: RaiseDisputeRequest): Observable<CardDispute> { return this.post<CardDispute>('/cards/disputes', req); }
  disputeCommand(id: string, command: string, body: unknown = {}): Observable<CardDispute> {
    return this.post<CardDispute>(`/cards/disputes/${id}/${command}`, body);
  }
  resolveDispute(id: string, req: ResolveDisputeRequest): Observable<CardDispute> {
    return this.post<CardDispute>(`/cards/disputes/${id}/resolve`, req);
  }
  listReasonCodes(scheme?: SchemeType): Observable<ChargebackReasonCode[]> {
    return this.get<ChargebackReasonCode[]>('/cards/disputes/reason-codes', scheme ? { scheme } : undefined);
  }

  // ── Terminal Simulator ─────────────────────────────────────────────────────
  simulatePurchase(req: SimulateRequest): Observable<SimulateResponse> { return this.post<SimulateResponse>('/simulate/purchase', req); }
  simulateWithdrawal(req: SimulateRequest): Observable<SimulateResponse> { return this.post<SimulateResponse>('/simulate/withdrawal', req); }
  simulateBalance(req: SimulateRequest): Observable<SimulateResponse> { return this.post<SimulateResponse>('/simulate/balance', req); }
  simulateReversal(req: SimulateRequest): Observable<SimulateResponse> { return this.post<SimulateResponse>('/simulate/reversal', req); }
  networkSignOn(): Observable<SimulateResponse> { return this.post<SimulateResponse>('/simulate/network/signon', {}); }
  networkEcho(): Observable<SimulateResponse> { return this.post<SimulateResponse>('/simulate/network/echo', {}); }

  // ── BIN Management ─────────────────────────────────────────────────────────
  listBins(): Observable<BinRange[]> { return this.get<BinRange[]>('/bins'); }
  createBin(req: BinRangeRequest): Observable<BinRange> { return this.post<BinRange>('/bins', req); }
  updateBin(id: string, req: BinRangeRequest): Observable<BinRange> { return this.put<BinRange>(`/bins/${id}`, req); }
  deleteBin(id: string): Observable<void> { return this.del<void>(`/bins/${id}`); }

  // ── Interchange ────────────────────────────────────────────────────────────
  listRates(): Observable<InterchangeRate[]> { return this.get<InterchangeRate[]>('/interchange/rates'); }
  createRate(req: InterchangeRateRequest): Observable<InterchangeRate> { return this.post<InterchangeRate>('/interchange/rates', req); }
  updateRate(id: string, req: InterchangeRateRequest): Observable<InterchangeRate> { return this.put<InterchangeRate>(`/interchange/rates/${id}`, req); }
  deleteRate(id: string): Observable<void> { return this.del<void>(`/interchange/rates/${id}`); }
  listSchemeFees(): Observable<SchemeFee[]> { return this.get<SchemeFee[]>('/interchange/fees'); }

  // ── API Keys ───────────────────────────────────────────────────────────────
  listApiKeys(): Observable<ApiKey[]> { return this.getCardApi<ApiKey[]>('/api-keys'); }
  issueApiKey(req: IssueApiKeyRequest): Observable<IssueApiKeyResponse> { return this.post<IssueApiKeyResponse>('/api-keys', req, true); }
  revokeApiKey(id: string): Observable<void> { return this.del<void>(`/api-keys/${id}`, true); }

  // ── Webhooks ───────────────────────────────────────────────────────────────
  listWebhooks(): Observable<Webhook[]> { return this.getCardApi<Webhook[]>('/webhooks'); }
  registerWebhook(req: RegisterWebhookRequest): Observable<Webhook> { return this.post<Webhook>('/webhooks', req, true); }
  deleteWebhook(id: string): Observable<void> { return this.del<void>(`/webhooks/${id}`, true); }
  listDeliveries(webhookId: string): Observable<WebhookDelivery[]> { return this.getCardApi<WebhookDelivery[]>(`/webhooks/${webhookId}/deliveries`); }
}
