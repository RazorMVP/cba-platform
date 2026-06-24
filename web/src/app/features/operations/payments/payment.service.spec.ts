import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { PaymentService, TransferRequest } from './payment.service';

describe('PaymentService', () => {
  let service: PaymentService;
  let api: Record<'get' | 'getPage' | 'post' | 'delete', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of({})),
      getPage: vi.fn().mockReturnValue(of({ content: [] })),
      post: vi.fn().mockReturnValue(of({})),
      delete: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({
      providers: [PaymentService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(PaymentService);
  });

  it('get() reads a single payment', () => {
    service.get('p1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/payments/p1');
  });

  it('getAccountPayments() pages by account', () => {
    service.getAccountPayments('a1', 1, 25).subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/payments/accounts/a1', 1, 25);
  });

  it('transfer() posts the transfer body', () => {
    const body: TransferRequest = {
      sourceAccountId: 'a1',
      destinationAccountId: 'a2',
      amount: 100,
      description: 'rent',
    };
    service.transfer(body).subscribe();
    expect(api.post).toHaveBeenCalledWith('/payments/transfer', body);
  });

  it('reverse() posts the reason to the reverse endpoint', () => {
    service.reverse('p1', 'duplicate').subscribe();
    expect(api.post).toHaveBeenCalledWith('/payments/p1/reverse', { reason: 'duplicate' });
  });

  it('listStandingOrders() filters by accountId', () => {
    service.listStandingOrders('a1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/payments/standing-orders', { accountId: 'a1' });
  });

  it('createStandingOrder() posts the order body', () => {
    const body = {
      sourceAccountId: 'a1',
      destinationAccountId: 'a2',
      amount: 50,
      frequency: 'MONTHLY' as const,
      startDate: '2026-01-01',
    };
    service.createStandingOrder(body).subscribe();
    expect(api.post).toHaveBeenCalledWith('/payments/standing-orders', body);
  });

  it('cancelStandingOrder() deletes by id', () => {
    service.cancelStandingOrder('so1').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/payments/standing-orders/so1');
  });

  it('initiateExternalPayment() posts to the external endpoint', () => {
    const body = {
      sourceAccountId: 'a1',
      amount: 1000,
      currencyCode: 'USD',
      network: 'SWIFT',
      beneficiaryName: 'ACME',
    };
    service.initiateExternalPayment(body).subscribe();
    expect(api.post).toHaveBeenCalledWith('/payments/external', body);
  });
});
