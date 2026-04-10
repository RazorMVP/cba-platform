import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';

export type TellerStatus  = 'INACTIVE' | 'ACTIVE' | 'CLOSED';
export type SessionStatus = 'OPEN' | 'CLOSED';
export type CashTxnType   = 'CASH_IN' | 'CASH_OUT';

export interface Teller {
  id: string;
  name: string;
  description?: string;
  branchCode: string;
  officeId?: string;
  status: TellerStatus;
  startDate: string;
  endDate?: string;
}

export interface TellerRequest {
  name: string;
  description?: string;
  branchCode: string;
  officeId?: string;
  startDate: string;
  endDate?: string;
}

export interface Cashier {
  id: string;
  tellerId: string;
  staffId: string;
  description?: string;
  startDate: string;
  endDate?: string;
  fullDay: boolean;
  startTime?: string;
  endTime?: string;
  active: boolean;
}

export interface CashierRequest {
  staffId: string;
  description?: string;
  startDate: string;
  endDate?: string;
  fullDay?: boolean;
  startTime?: string;
  endTime?: string;
}

export interface TellerSession {
  id: string;
  tellerId: string;
  cashierId: string;
  sessionDate: string;
  openingBalance: number;
  closingBalance?: number;
  actualCash?: number;
  difference?: number;
  currencyCode: string;
  status: SessionStatus;
  settlementNote?: string;
  openedAt: string;
  closedAt?: string;
}

export interface OpenSessionRequest {
  openingBalance: number;
  currencyCode: string;
}

export interface CloseSessionRequest {
  actualCash: number;
  settlementNote?: string;
}

export interface CashTransaction {
  id: string;
  sessionId: string;
  tellerId: string;
  cashierId: string;
  accountId?: string;
  transactionType: CashTxnType;
  amount: number;
  currencyCode: string;
  description?: string;
  referenceNumber: string;
  transactionDate: string;
}

export interface CashTransactionRequest {
  transactionType: CashTxnType;
  amount: number;
  currencyCode?: string;
  accountId?: string;
  description?: string;
}

@Injectable({ providedIn: 'root' })
export class TellerService {
  private readonly api = inject(ApiService);

  // ── Tellers ────────────────────────────────────────────────────────────────

  list(): Observable<Teller[]> {
    return this.api.get<Teller[]>('/tellers');
  }

  get(id: string): Observable<Teller> {
    return this.api.get<Teller>(`/tellers/${id}`);
  }

  create(body: TellerRequest): Observable<Teller> {
    return this.api.post<Teller>('/tellers', body);
  }

  update(id: string, body: TellerRequest): Observable<Teller> {
    return this.api.put<Teller>(`/tellers/${id}`, body);
  }

  activate(id: string): Observable<Teller> {
    return this.api.post<Teller>(`/tellers/${id}/activate`, {});
  }

  close(id: string): Observable<Teller> {
    return this.api.post<Teller>(`/tellers/${id}/close`, {});
  }

  // ── Cashiers ───────────────────────────────────────────────────────────────

  getCashiers(tellerId: string): Observable<Cashier[]> {
    return this.api.get<Cashier[]>(`/tellers/${tellerId}/cashiers`);
  }

  assignCashier(tellerId: string, body: CashierRequest): Observable<Cashier> {
    return this.api.post<Cashier>(`/tellers/${tellerId}/cashiers`, body);
  }

  // ── Sessions ───────────────────────────────────────────────────────────────

  getSessions(tellerId: string): Observable<TellerSession[]> {
    return this.api.get<TellerSession[]>(`/tellers/${tellerId}/sessions`);
  }

  getSession(tellerId: string, sessionId: string): Observable<TellerSession> {
    return this.api.get<TellerSession>(`/tellers/${tellerId}/sessions/${sessionId}`);
  }

  openSession(tellerId: string, cashierId: string, body: OpenSessionRequest): Observable<TellerSession> {
    return this.api.post<TellerSession>(`/tellers/${tellerId}/cashiers/${cashierId}/sessions`, body);
  }

  closeSession(tellerId: string, sessionId: string, body: CloseSessionRequest): Observable<TellerSession> {
    return this.api.post<TellerSession>(`/tellers/${tellerId}/sessions/${sessionId}/settle`, body);
  }

  // ── Cash Transactions ──────────────────────────────────────────────────────

  getSessionTransactions(tellerId: string, sessionId: string): Observable<CashTransaction[]> {
    return this.api.get<CashTransaction[]>(`/tellers/${tellerId}/sessions/${sessionId}/transactions`);
  }

  recordTransaction(tellerId: string, sessionId: string, body: CashTransactionRequest): Observable<CashTransaction> {
    return this.api.post<CashTransaction>(`/tellers/${tellerId}/sessions/${sessionId}/transactions`, body);
  }
}
