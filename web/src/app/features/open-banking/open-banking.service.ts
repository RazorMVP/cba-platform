import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api/api.service';

export type ConsentStatus = 'AWAITING_AUTHORISATION' | 'AUTHORISED' | 'REVOKED' | 'EXPIRED';
export type ConsentType = 'AISP' | 'PISP' | 'CBPII';

export interface Consent {
  id: string;
  consentId: string;
  customerId: string;
  customerName: string;
  tppName: string;
  tppClientId: string;
  consentType: ConsentType;
  scopes: string[];
  status: ConsentStatus;
  createdAt: string;
  expirationDateTime: string | null;
  authorisedAt: string | null;
  revokedAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class OpenBankingService {
  private readonly api = inject(ApiService);

  listConsents(type?: ConsentType, status?: ConsentStatus): Observable<Consent[]> {
    const params: Record<string, string> = {};
    if (type)   params['type']   = type;
    if (status) params['status'] = status;
    return this.api.get<Consent[]>('/open-banking/v3.1/consents', Object.keys(params).length ? params : undefined);
  }

  getConsent(id: string): Observable<Consent> {
    return this.api.get<Consent>(`/open-banking/v3.1/consents/${id}`);
  }

  authoriseConsent(id: string): Observable<Consent> {
    return this.api.command<Consent>(`/open-banking/v3.1/consents/${id}`, 'authorise');
  }

  revokeConsent(id: string): Observable<Consent> {
    return this.api.command<Consent>(`/open-banking/v3.1/consents/${id}`, 'revoke');
  }
}
