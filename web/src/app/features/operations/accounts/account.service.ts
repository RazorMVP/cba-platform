import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { PageResponse } from '../../../core/models/api-response.model';

export interface Account {
  id: string;
  accountNumber: string;
  customerId: string;
  customerName?: string;
  accountType: 'SAVINGS' | 'CHECKING' | 'FIXED_DEPOSIT';
  status: 'SUBMITTED' | 'APPROVED' | 'ACTIVE' | 'FROZEN' | 'DORMANT' | 'REJECTED' | 'CLOSED';
  balance: number;
  availableBalance?: number;
  onHoldAmount?: number;
  currencyCode: string;
  productId: string;
  productName?: string;
  openedDate: string;
  lastTransactionDate?: string;
  updatedAt?: string;
}

export interface AccountHold {
  id: string;
  accountId: string;
  amount: number;
  reason: string;
  referenceNumber?: string;
  status: 'ACTIVE' | 'RELEASED' | 'EXPIRED';
  expiryDate?: string;
  releasedAt?: string;
  releasedBy?: string;
  createdAt: string;
  createdBy?: string;
}

export interface AccountHoldRequest {
  amount: number;
  reason: string;
  expiryDate?: string;
}

export interface Transaction {
  id: string;
  accountId: string;
  transactionType: string;
  amount: number;
  runningBalance: number;
  referenceNumber?: string;
  description?: string;
  transactionDate: string;
}

export interface AccountCreateRequest {
  customerId: string;
  productId: string;
  accountType: Account['accountType'];
  currencyCode?: string;
}

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly api = inject(ApiService);

  list(page = 0, size = 20, customerId?: string): Observable<PageResponse<Account>> {
    const params: Record<string, string> = {};
    if (customerId) params['customerId'] = customerId;
    return this.api.getPage<Account>('/accounts', page, size, params);
  }

  get(id: string): Observable<Account> {
    return this.api.get<Account>(`/accounts/${id}`);
  }

  create(body: AccountCreateRequest): Observable<Account> {
    return this.api.post<Account>('/accounts', body);
  }

  approve(id: string): Observable<Account> {
    return this.api.post<Account>(`/accounts/${id}?command=approve`, {});
  }

  activate(id: string): Observable<Account> {
    return this.api.post<Account>(`/accounts/${id}?command=activate`, {});
  }

  reject(id: string): Observable<Account> {
    return this.api.post<Account>(`/accounts/${id}?command=reject`, {});
  }

  freeze(id: string): Observable<Account> {
    return this.api.putParams<Account>(`/accounts/${id}/status`, { status: 'FROZEN' });
  }

  unfreeze(id: string): Observable<Account> {
    return this.api.putParams<Account>(`/accounts/${id}/status`, { status: 'ACTIVE' });
  }

  close(id: string): Observable<Account> {
    return this.api.putParams<Account>(`/accounts/${id}/status`, { status: 'CLOSED' });
  }

  getTransactions(id: string, page = 0, size = 20, transactionType?: string): Observable<PageResponse<Transaction>> {
    const params: Record<string, string> = {};
    if (transactionType) params['transactionType'] = transactionType;
    return this.api.getPage<Transaction>(`/accounts/${id}/transactions`, page, size, params);
  }

  deposit(id: string, amount: number, description?: string): Observable<Transaction> {
    const params: Record<string, string> = { amount: String(amount) };
    if (description) params['description'] = description;
    return this.api.postParams<Transaction>(`/accounts/${id}/deposit`, params);
  }

  withdraw(id: string, amount: number, description?: string): Observable<Transaction> {
    const params: Record<string, string> = { amount: String(amount) };
    if (description) params['description'] = description;
    return this.api.postParams<Transaction>(`/accounts/${id}/withdraw`, params);
  }

  reactivate(id: string): Observable<Account> {
    return this.api.post<Account>(`/accounts/${id}?command=reactivate`, {});
  }

  getHolds(id: string): Observable<AccountHold[]> {
    return this.api.get<AccountHold[]>(`/accounts/${id}/holds`);
  }

  placeHold(id: string, req: AccountHoldRequest): Observable<AccountHold> {
    return this.api.post<AccountHold>(`/accounts/${id}/holds`, req);
  }

  releaseHold(accountId: string, holdId: string): Observable<AccountHold> {
    return this.api.delete<AccountHold>(`/accounts/${accountId}/holds/${holdId}`);
  }

  getStatement(id: string, from: string, to: string): Observable<Record<string, unknown>> {
    return this.api.get<Record<string, unknown>>(`/accounts/${id}/statement`, { from, to });
  }

  getTemplate(id: string): Observable<Record<string, unknown>> {
    return this.api.get<Record<string, unknown>>(`/accounts/${id}/template`);
  }
}
