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
  status: 'ACTIVE' | 'DORMANT' | 'CLOSED' | 'FROZEN';
  balance: number;
  currencyCode: string;
  productId: string;
  productName?: string;
  openedDate: string;
  updatedAt?: string;
}

export interface Transaction {
  id: string;
  accountId: string;
  transactionType: 'CREDIT' | 'DEBIT';
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

  freeze(id: string): Observable<Account> {
    return this.api.putParams<Account>(`/accounts/${id}/status`, { status: 'FROZEN' });
  }

  unfreeze(id: string): Observable<Account> {
    return this.api.putParams<Account>(`/accounts/${id}/status`, { status: 'ACTIVE' });
  }

  close(id: string): Observable<Account> {
    return this.api.putParams<Account>(`/accounts/${id}/status`, { status: 'CLOSED' });
  }

  getTransactions(id: string, page = 0, size = 20): Observable<PageResponse<Transaction>> {
    return this.api.getPage<Transaction>(`/accounts/${id}/transactions`, page, size);
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
}
