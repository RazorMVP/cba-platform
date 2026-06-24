import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard';
import { DashboardService } from './dashboard.service';

type DashboardSvcMock = Record<
  'getKpis' | 'getLoanPortfolio' | 'getRecentTransactions' |
  'getKycPendingCustomers' | 'getDepositAnalytics' | 'getRepaymentAnalytics',
  ReturnType<typeof vi.fn>
>;

describe('DashboardComponent', () => {
  let svc: DashboardSvcMock;

  beforeEach(() => {
    svc = {
      getKpis: vi.fn().mockReturnValue(of({
        totalCustomers: 5, activeLoans: 2, totalDeposits: 9, depositBalance: 1000,
        todayTransactions: 3, loansInArrears: 1, kycPending: 4,
      })),
      getLoanPortfolio: vi.fn().mockReturnValue(of([
        { label: 'Current', pct: 80, color: '#16a34a', count: 40 },
      ])),
      getRecentTransactions: vi.fn().mockReturnValue(of([
        { id: 't1', accountNumber: '001', customerName: 'Jane', transactionType: 'TRANSFER_COMPLETED', amount: 50, runningBalance: 100, createdAt: '2026-06-01' },
      ])),
      getKycPendingCustomers: vi.fn().mockReturnValue(of([
        { id: 'c1', fullName: 'John Doe', initials: 'JD', kycStatus: 'PENDING_KYC', createdAt: '2026-06-01' },
      ])),
      getDepositAnalytics: vi.fn().mockReturnValue(of({
        savingsCount: 1, savingsBalance: 10, checkingCount: 1, checkingBalance: 20,
        fixedDepositCount: 0, fixedDepositBalance: 0, newThisMonth: 1, averageBalance: 15,
      })),
      getRepaymentAnalytics: vi.fn().mockReturnValue(of({
        installmentsDueThisMonth: 5, installmentsPaidThisMonth: 4, amountDueThisMonth: 500,
        amountCollectedThisMonth: 400, collectionRate: 80, overdueInstallmentCount: 1, overdueBalance: 100,
      })),
    };

    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideRouter([]), { provide: DashboardService, useValue: svc }],
    });
  });

  it('loads all six data sources on init and renders without error', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges(); // triggers ngOnInit + full template render

    const c = fixture.componentInstance;
    expect(svc.getKpis).toHaveBeenCalled();
    expect(svc.getLoanPortfolio).toHaveBeenCalled();
    expect(svc.getRecentTransactions).toHaveBeenCalled();
    expect(svc.getKycPendingCustomers).toHaveBeenCalled();
    expect(svc.getDepositAnalytics).toHaveBeenCalled();
    expect(svc.getRepaymentAnalytics).toHaveBeenCalled();

    expect(c.loading).toBe(false);
    expect(c.kpis.totalCustomers).toBe(5);
    expect(c.recentTransactions).toHaveLength(1);
    expect(c.kycPending).toHaveLength(1);
  });

  it('clears the loading flag even when KPI loading errors', () => {
    svc.getKpis.mockReturnValue(throwError(() => new Error('boom')));
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.loading).toBe(false);
  });

  describe('presentation helpers', () => {
    let c: DashboardComponent;
    beforeEach(() => {
      c = TestBed.createComponent(DashboardComponent).componentInstance;
    });

    it('avatarColor wraps around the palette', () => {
      expect(c.avatarColor(0)).toBe(c.avatarColor(6)); // 6 % 6 === 0
    });

    it('txnAmountClass distinguishes credit vs debit', () => {
      expect(c.txnAmountClass(100)).toBe('amount--credit');
      expect(c.txnAmountClass(-1)).toBe('amount--debit');
    });

    it('txnBadgeVariant maps status keywords', () => {
      expect(c.txnBadgeVariant('TRANSFER_COMPLETED')).toBe('success');
      expect(c.txnBadgeVariant('PENDING_AUTH')).toBe('warning');
      expect(c.txnBadgeVariant('FAILED')).toBe('error');
      expect(c.txnBadgeVariant('SOMETHING')).toBe('info');
    });

    it('collectionBarColor uses rate thresholds', () => {
      expect(c.collectionBarColor(95)).toBe('#16a34a');
      expect(c.collectionBarColor(75)).toBe('#ca8a04');
      expect(c.collectionBarColor(50)).toBe('#dc2626');
    });

    it('depositBalanceFormatted formats a value or falls back to a dash', () => {
      c.kpis = { depositBalance: 1000 };
      expect(c.depositBalanceFormatted).toBe('$1,000');
      c.kpis = {};
      expect(c.depositBalanceFormatted).toBe('—');
    });
  });
});
