import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  let service: DashboardService;
  let api: Record<'get' | 'getPage', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of({})),
      getPage: vi.fn().mockReturnValue(of({ content: [], totalElements: 0 })),
    };
    TestBed.configureTestingModule({
      providers: [DashboardService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(DashboardService);
  });

  describe('getKpis', () => {
    it('returns the /dashboard payload directly when available', () => {
      const kpi = {
        totalCustomers: 5, activeLoans: 2, totalDeposits: 9, depositBalance: 100,
        todayTransactions: 3, loansInArrears: 1, kycPending: 4,
      };
      api.get.mockReturnValue(of(kpi));
      let result: unknown;
      service.getKpis().subscribe(r => (result = r));

      expect(api.get).toHaveBeenCalledWith('/dashboard');
      expect(result).toEqual(kpi);
    });

    it('falls back to per-resource counts when /dashboard errors', () => {
      api.get.mockReturnValue(throwError(() => new Error('404')));
      api.getPage
        .mockReturnValueOnce(of({ totalElements: 11 })) // customers
        .mockReturnValueOnce(of({ totalElements: 7 }))  // loans
        .mockReturnValueOnce(of({ totalElements: 3 })); // accounts

      let result: { totalCustomers: number; activeLoans: number; totalDeposits: number } | undefined;
      service.getKpis().subscribe(r => (result = r));

      expect(result?.totalCustomers).toBe(11);
      expect(result?.activeLoans).toBe(7);
      expect(result?.totalDeposits).toBe(3);
    });
  });

  describe('getLoanPortfolio', () => {
    it('maps the analytics payload into 4 colour-coded buckets', () => {
      api.get.mockReturnValue(of({
        pctCurrent: 80, pct30to60: 10, pct60to90: 6, pct90plus: 4,
        countActive: 40, countInArrears: 3, countWrittenOff: 2,
      }));
      let buckets: { label: string; pct: number; count?: number }[] = [];
      service.getLoanPortfolio().subscribe(b => (buckets = b));

      expect(api.get).toHaveBeenCalledWith('/dashboard/analytics/loans');
      expect(buckets).toHaveLength(4);
      expect(buckets[0]).toMatchObject({ pct: 80, count: 40 });
      expect(buckets[3]).toMatchObject({ pct: 4, count: 2 });
    });

    it('returns zeroed buckets when the endpoint errors', () => {
      api.get.mockReturnValue(throwError(() => new Error('500')));
      let buckets: { pct: number }[] = [];
      service.getLoanPortfolio().subscribe(b => (buckets = b));

      expect(buckets).toHaveLength(4);
      expect(buckets.every(b => b.pct === 0)).toBe(true);
    });
  });

  it('getRecentTransactions pages /transactions and unwraps content (empty on error)', () => {
    api.getPage.mockReturnValue(of({ content: [{ id: 't1' }] }));
    let txns: unknown;
    service.getRecentTransactions().subscribe(t => (txns = t));
    expect(api.getPage).toHaveBeenCalledWith('/transactions', 0, 10);
    expect(txns).toEqual([{ id: 't1' }]);

    api.getPage.mockReturnValue(throwError(() => new Error('x')));
    let fallback: unknown;
    service.getRecentTransactions().subscribe(t => (fallback = t));
    expect(fallback).toEqual([]);
  });

  it('getKycPendingCustomers builds full name + initials', () => {
    api.getPage.mockReturnValue(of({
      content: [{ id: 'c1', firstName: 'John', lastName: 'Doe', kycStatus: 'PENDING_KYC', createdAt: 'x' }],
    }));
    let list: { fullName: string; initials: string }[] = [];
    service.getKycPendingCustomers().subscribe(l => (list = l));

    expect(api.getPage).toHaveBeenCalledWith('/customers', 0, 5, { kycStatus: 'PENDING_KYC' });
    expect(list[0].fullName).toBe('John Doe');
    expect(list[0].initials).toBe('JD');
  });
});
