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
    return this.api.command<Account>(`/accounts/${id}`, 'freeze');
  }

  close(id: string): Observable<Account> {
    return this.api.command<Account>(`/accounts/${id}`, 'close');
  }
}
