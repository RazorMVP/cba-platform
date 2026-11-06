import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { PageResponse } from '../../core/models/api-response.model';

export type ConsentStatus = 'AWAITING_AUTHORISATION' | 'AUTHORISED' | 'CONSUMED' | 'REVOKED' | 'EXPIRED';
export type ConsentType = 'AISP' | 'PISP' | 'CBPII';

/** The one payment a PISP consent allows. */
export interface PaymentInitiation {
  instructedAmount: number;
  currency: string;
  creditorAccountId: string;
  debtorAccountId: string | null;
  reference: string | null;
}

/** A consent as bank staff see it — `StaffConsentResponse` on the backend. */
export interface Consent {
  consentId: string;
  consentType: ConsentType;
  customerId: string;
  customerName: string | null;
  tppClientId: string;
  tppName: string | null;
  scopes: string[];
  status: ConsentStatus;
  expiryDate: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  initiation: PaymentInitiation | null;
  paymentId: string | null;
  consumedAt: string | null;
}

export interface ConsentFilters {
  type?: ConsentType;
  status?: ConsentStatus;
  customerId?: string;
  tppClientId?: string;
}

/**
 * Staff view of Open Banking consents: `/api/v1/open-banking/consents`. Authorising is the customer's
 * own step, so there is no authorise call here; staff can revoke on the customer's behalf.
 */
@Injectable({ providedIn: 'root' })
export class OpenBankingService {
  private readonly api = inject(ApiService);
  private readonly base = '/open-banking/consents';

  listConsents(filters: ConsentFilters = {}, page = 0, size = 20): Observable<PageResponse<Consent>> {
    const params: Record<string, string> = {};
    Object.entries(filters).forEach(([k, v]) => { if (v) params[k] = v; });
    return this.api.getPage<Consent>(this.base, page, size, params);
  }

  getConsent(consentId: string): Observable<Consent> {
    return this.api.get<Consent>(`${this.base}/${encodeURIComponent(consentId)}`);
  }

  revokeConsent(consentId: string, reason: string): Observable<Consent> {
    return this.api.post<Consent>(`${this.base}/${encodeURIComponent(consentId)}/revoke`, { reason });
  }
}
