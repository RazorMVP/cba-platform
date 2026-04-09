import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { PageResponse } from '../../../core/models/api-response.model';

export interface Customer {
  id: string;
  externalId?: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  kycStatus: 'PENDING_KYC' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
  createdAt: string;
  updatedAt?: string;
}

export type KycStatus = Customer['kycStatus'];

export interface CustomerCreateRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  nationalId?: string;
  dateOfBirth?: string;
}

@Injectable({ providedIn: 'root' })
export class CustomerService {
  private readonly api = inject(ApiService);

  list(page = 0, size = 20, search?: string, kycStatus?: KycStatus): Observable<PageResponse<Customer>> {
    const params: Record<string, string> = {};
    if (search)    params['search']    = search;
    if (kycStatus) params['kycStatus'] = kycStatus;
    return this.api.getPage<Customer>('/customers', page, size, params);
  }

  get(id: string): Observable<Customer> {
    return this.api.get<Customer>(`/customers/${id}`);
  }

  create(body: CustomerCreateRequest): Observable<Customer> {
    return this.api.post<Customer>('/customers', body);
  }

  updateKycStatus(id: string, status: KycStatus): Observable<Customer> {
    return this.api.command<Customer>(`/customers/${id}/kyc-status`, 'update', { status });
  }
}
