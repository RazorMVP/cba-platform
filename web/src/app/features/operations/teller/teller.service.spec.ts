import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { TellerService } from './teller.service';

describe('TellerService', () => {
  let service: TellerService;
  let api: Record<'get' | 'post' | 'put', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of({})),
      post: vi.fn().mockReturnValue(of({})),
      put: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({
      providers: [TellerService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(TellerService);
  });

  it('teller CRUD routes correctly', () => {
    service.list().subscribe();
    expect(api.get).toHaveBeenCalledWith('/tellers');
    service.get('t1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/tellers/t1');
    const body = { name: 'Main', branchCode: '001', startDate: '2026-01-01' };
    service.create(body).subscribe();
    expect(api.post).toHaveBeenCalledWith('/tellers', body);
    service.update('t1', body).subscribe();
    expect(api.put).toHaveBeenCalledWith('/tellers/t1', body);
  });

  it('activate/close post to dedicated sub-paths (not ?command=)', () => {
    service.activate('t1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/tellers/t1/activate', {});
    service.close('t1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/tellers/t1/close', {});
  });

  it('cashiers route under the teller', () => {
    service.getCashiers('t1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/tellers/t1/cashiers');
    const body = { staffId: 's1', startDate: '2026-01-01' };
    service.assignCashier('t1', body).subscribe();
    expect(api.post).toHaveBeenCalledWith('/tellers/t1/cashiers', body);
  });

  it('session lifecycle: open under cashier, settle under session', () => {
    service.getSessions('t1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/tellers/t1/sessions');
    service.getSession('t1', 's9').subscribe();
    expect(api.get).toHaveBeenCalledWith('/tellers/t1/sessions/s9');

    const open = { openingBalance: 500, currencyCode: 'USD' };
    service.openSession('t1', 'c1', open).subscribe();
    expect(api.post).toHaveBeenCalledWith('/tellers/t1/cashiers/c1/sessions', open);

    const close = { actualCash: 480 };
    service.closeSession('t1', 's9', close).subscribe();
    expect(api.post).toHaveBeenCalledWith('/tellers/t1/sessions/s9/settle', close);
  });

  it('cash transactions route under the session', () => {
    service.getSessionTransactions('t1', 's9').subscribe();
    expect(api.get).toHaveBeenCalledWith('/tellers/t1/sessions/s9/transactions');
    const txn = { transactionType: 'CASH_IN' as const, amount: 100 };
    service.recordTransaction('t1', 's9', txn).subscribe();
    expect(api.post).toHaveBeenCalledWith('/tellers/t1/sessions/s9/transactions', txn);
  });
});
