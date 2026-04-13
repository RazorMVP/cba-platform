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
  dateOfBirth?: string;
  kycStatus: KycStatus;
  // Lifecycle dates
  activationDate?: string;
  closureDate?: string;
  rejectionDate?: string;
  withdrawalDate?: string;
  // Lifecycle reasons
  closureReason?: string;
  rejectionReason?: string;
  withdrawalReason?: string;
  // Staff / office
  staffId?: string;
  officeId?: string;
  // Transfer
  transferToOfficeId?: string;
  transferDate?: string;
  transferNote?: string;
  // Timestamps
  createdAt: string;
  updatedAt?: string;
}

export type KycStatus =
  | 'PENDING_KYC'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'CLOSED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'TRANSFER_IN_PROGRESS';

export interface CustomerCreateRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  nationalId?: string;
  dateOfBirth?: string;
}

export interface CustomerUpdateRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string;
  nationalId?: string;
  dateOfBirth?: string;
  notes?: string;
}

export interface CustomerCommandRequest {
  reason?: string;
  staffId?: string;
  destinationOfficeId?: string;
  transferDate?: string;
  transferNote?: string;
}

export interface ClientIdentifier {
  id: string;
  documentType: string;
  documentKey: string;
  expiryDate?: string;
  active: boolean;
}

export interface ClientAddress {
  id: string;
  addressType: 'HOME' | 'WORK' | 'MAILING';
  addressLine1: string;
  addressLine2?: string;
  city: string;
  stateProvince?: string;
  postalCode?: string;
  countryCode: string;
}

export interface Beneficiary {
  id: string;
  name: string;
  accountNumber: string;
  bankName?: string;
  transferLimit?: number;
  active: boolean;
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

  update(id: string, body: CustomerUpdateRequest): Observable<Customer> {
    return this.api.put<Customer>(`/customers/${id}`, body);
  }

  /** Mifos command pattern: POST /customers/{id}?command=... */
  executeCommand(id: string, command: string, payload: CustomerCommandRequest = {}): Observable<Customer> {
    return this.api.post<Customer>(`/customers/${id}?command=${command}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/customers/${id}`);
  }

  /** Legacy — kept for backward compat; prefer executeCommand('activate') */
  updateKycStatus(id: string, status: KycStatus): Observable<Customer> {
    return this.api.put<Customer>(`/customers/${id}/kyc-status`, { kycStatus: status });
  }

  getIdentifiers(id: string): Observable<ClientIdentifier[]> {
    return this.api.get<ClientIdentifier[]>(`/clients/${id}/identifiers`);
  }

  getAddresses(id: string): Observable<ClientAddress[]> {
    return this.api.get<ClientAddress[]>(`/clients/${id}/addresses`);
  }

  getBeneficiaries(id: string): Observable<Beneficiary[]> {
    return this.api.get<Beneficiary[]>(`/clients/${id}/beneficiaries`);
  }
}
