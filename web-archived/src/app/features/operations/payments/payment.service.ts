import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { PageResponse } from '../../../core/models/api-response.model';

export type PaymentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'REVERSED';
export type PaymentType   = 'INTERNAL_TRANSFER' | 'EXTERNAL_PAYMENT' | 'STANDING_ORDER' | 'BILL_PAYMENT';
export type SOFrequency   = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY';
export type SOStatus      = 'ACTIVE' | 'PAUSED' | 'CANCELLED' | 'COMPLETED';

export interface Payment {
  id: string;
  referenceNumber: string;
  paymentType: PaymentType;
  sourceAccountId: string;
  sourceAccountNumber: string;
  destinationAccountId: string;
  destinationAccountNumber: string;
  amount: number;
  currencyCode: string;
  description?: string;
  status: PaymentStatus;
  executedDate?: string;
  createdAt: string;
  // Cross-currency fields (null for same-currency)
  crossCurrency: boolean;
  sourceCurrency?: string;
  sourceAmount?: number;
  destinationCurrency?: string;
  destinationAmount?: number;
  exchangeRateUsed?: number;
}

export interface TransferRequest {
  sourceAccountId: string;
  destinationAccountId: string;
  amount: number;
  description?: string;
}

export interface StandingOrder {
  id: string;
  sourceAccountId: string;
  destinationAccountId: string;
  amount: number;
  currencyCode: string;
  frequency: SOFrequency;
  startDate: string;
  endDate?: string;
  nextExecutionDate?: string;
  description?: string;
  status: SOStatus;
  lastExecutedAt?: string;
  createdAt: string;
}

export interface StandingOrderRequest {
  sourceAccountId: string;
  destinationAccountId: string;
  amount: number;
  currencyCode?: string;
  frequency: SOFrequency;
  startDate: string;
  endDate?: string;
  description?: string;
}

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly api = inject(ApiService);

  /** GET /payments/{id} */
  get(id: string): Observable<Payment> {
    return this.api.get<Payment>(`/payments/${id}`);
  }

  /** GET /payments/accounts/{accountId}?page&size */
  getAccountPayments(accountId: string, page = 0, size = 20): Observable<PageResponse<Payment>> {
    return this.api.getPage<Payment>(`/payments/accounts/${accountId}`, page, size);
  }

  /** POST /payments/transfer */
  transfer(body: TransferRequest): Observable<Payment> {
    return this.api.post<Payment>('/payments/transfer', body);
  }

  /** POST /payments/{id}/reverse */
  reverse(id: string, reason: string): Observable<Payment> {
    return this.api.post<Payment>(`/payments/${id}/reverse`, { reason });
  }

  /** GET /payments/standing-orders?accountId= */
  listStandingOrders(accountId: string): Observable<StandingOrder[]> {
    return this.api.get<StandingOrder[]>('/payments/standing-orders', { accountId });
  }

  /** POST /payments/standing-orders */
  createStandingOrder(body: StandingOrderRequest): Observable<StandingOrder> {
    return this.api.post<StandingOrder>('/payments/standing-orders', body);
  }

  /** DELETE /payments/standing-orders/{id} */
  cancelStandingOrder(id: string): Observable<StandingOrder> {
    return this.api.delete<StandingOrder>(`/payments/standing-orders/${id}`);
  }
}
