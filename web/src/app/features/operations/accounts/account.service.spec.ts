import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { AccountService } from './account.service';

/**
 * Tests AccountService's routing contract against a mocked ApiService. The value
 * here is locking the exact path + param shape for each operation — especially
 * the easy-to-break cases: paged list vs single get, lifecycle commands via
 * `?command=` URLs, status changes via putParams, and money ops via postParams
 * with the amount coerced to a string.
 */
describe('AccountService', () => {
  let service: AccountService;
  let api: Record<'get' | 'getPage' | 'post' | 'putParams' | 'postParams' | 'delete', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of({})),
      getPage: vi.fn().mockReturnValue(of({ content: [] })),
      post: vi.fn().mockReturnValue(of({})),
      putParams: vi.fn().mockReturnValue(of({})),
      postParams: vi.fn().mockReturnValue(of({})),
      delete: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({
      providers: [AccountService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(AccountService);
  });

  it('is created', () => {
    expect(service).toBeTruthy();
  });

  it('list() pages /accounts with an optional customerId filter', () => {
    service.list(2, 50, 'cust-1').subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/accounts', 2, 50, { customerId: 'cust-1' });
  });

  it('list() omits the filter when no customerId is given', () => {
    service.list().subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/accounts', 0, 20, {});
  });

  it('get() reads a single account', () => {
    service.get('a1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/accounts/a1');
  });

  it('create() posts the account body', () => {
    const body = { customerId: 'c1', productId: 'p1', accountType: 'SAVINGS' as const };
    service.create(body).subscribe();
    expect(api.post).toHaveBeenCalledWith('/accounts', body);
  });

  it.each([
    ['approve', (s: AccountService) => s.approve('a1')],
    ['activate', (s: AccountService) => s.activate('a1')],
    ['reject', (s: AccountService) => s.reject('a1')],
    ['reactivate', (s: AccountService) => s.reactivate('a1')],
    ['postInterest', (s: AccountService) => s.postInterest('a1')],
  ])('%s() posts the lifecycle command URL with an empty body', (command, call) => {
    call(service).subscribe();
    expect(api.post).toHaveBeenCalledWith(`/accounts/a1?command=${command}`, {});
  });

  it.each([
    ['freeze', 'FROZEN', (s: AccountService) => s.freeze('a1')],
    ['unfreeze', 'ACTIVE', (s: AccountService) => s.unfreeze('a1')],
    ['close', 'CLOSED', (s: AccountService) => s.close('a1')],
  ])('%s() sets status=%s via putParams', (_name, status, call) => {
    call(service).subscribe();
    expect(api.putParams).toHaveBeenCalledWith('/accounts/a1/status', { status });
  });

  it('deposit() coerces the amount to a string and includes the description', () => {
    service.deposit('a1', 250.5, 'salary').subscribe();
    expect(api.postParams).toHaveBeenCalledWith('/accounts/a1/deposit', {
      amount: '250.5',
      description: 'salary',
    });
  });

  it('withdraw() omits description when not provided', () => {
    service.withdraw('a1', 40).subscribe();
    expect(api.postParams).toHaveBeenCalledWith('/accounts/a1/withdraw', { amount: '40' });
  });

  it('getTransactions() pages with an optional transactionType filter', () => {
    service.getTransactions('a1', 0, 20, 'INTEREST_CREDIT').subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/accounts/a1/transactions', 0, 20, {
      transactionType: 'INTEREST_CREDIT',
    });
  });

  it('holds: get, place, release route correctly', () => {
    service.getHolds('a1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/accounts/a1/holds');

    const req = { amount: 100, reason: 'pending' };
    service.placeHold('a1', req).subscribe();
    expect(api.post).toHaveBeenCalledWith('/accounts/a1/holds', req);

    service.releaseHold('a1', 'h9').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/accounts/a1/holds/h9');
  });

  it('getStatement() passes the from/to date range', () => {
    service.getStatement('a1', '2026-01-01', '2026-06-01').subscribe();
    expect(api.get).toHaveBeenCalledWith('/accounts/a1/statement', {
      from: '2026-01-01',
      to: '2026-06-01',
    });
  });

  it('getOpenAccountTemplate() reads the template endpoint', () => {
    service.getOpenAccountTemplate().subscribe();
    expect(api.get).toHaveBeenCalledWith('/accounts/template');
  });
});
