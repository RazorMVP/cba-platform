import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { LoansListComponent } from './loans-list';
import { LoanService } from './loan.service';

describe('LoansListComponent', () => {
  let svc: { list: ReturnType<typeof vi.fn>; getSchedule: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    svc = {
      list: vi.fn().mockReturnValue(
        of({ content: [{ id: 'l1', status: 'ACTIVE' }], totalElements: 1, totalPages: 1, size: 20, number: 0 }),
      ),
      getSchedule: vi.fn().mockReturnValue(of([])),
    };
    TestBed.configureTestingModule({
      imports: [LoansListComponent],
      providers: [provideRouter([]), { provide: LoanService, useValue: svc }],
    });
  });

  it('loads loans on init (no status filter)', () => {
    const fixture = TestBed.createComponent(LoansListComponent);
    fixture.detectChanges();
    expect(svc.list).toHaveBeenCalledWith(0, 20, undefined);
    expect(fixture.componentInstance.loans).toHaveLength(1);
  });

  it('selectLoan loads the repayment schedule', () => {
    svc.getSchedule.mockReturnValue(of([{ id: 'i1', status: 'OVERDUE' }]));
    const c = TestBed.createComponent(LoansListComponent).componentInstance;
    c.selectLoan({ id: 'l9' } as never);
    expect(c.selectedLoan?.id).toBe('l9');
    expect(svc.getSchedule).toHaveBeenCalledWith('l9');
    expect(c.schedule).toHaveLength(1);
    expect(c.scheduleLoading).toBe(false);
  });

  describe('computed values', () => {
    let c: LoansListComponent;
    beforeEach(() => {
      c = TestBed.createComponent(LoansListComponent).componentInstance;
    });

    it('repaidPct = round((1 - outstanding/principal) * 100), 0 when no principal', () => {
      expect(c.repaidPct({ principalAmount: 1000, outstandingBalance: 250 } as never)).toBe(75);
      expect(c.repaidPct({ principalAmount: 0, outstandingBalance: 0 } as never)).toBe(0);
    });

    it('statusVariant + statusLabel map known statuses (fallbacks for unknown)', () => {
      expect(c.statusVariant('ACTIVE')).toBe('primary');
      expect(c.statusVariant('IN_ARREARS')).toBe('error');
      expect(c.statusVariant('CLOSED_OBLIGATIONS_MET')).toBe('neutral');
      expect(c.statusLabel('UNDER_REVIEW')).toBe('Under Review');
      expect(c.statusLabel('CLOSED_OBLIGATIONS_MET')).toBe('Closed');
    });

    it('overdueCount + overdueTotal aggregate only OVERDUE installments', () => {
      c.schedule = [
        { status: 'OVERDUE', totalDue: 100, principalPaid: 10, interestPaid: 5 },
        { status: 'PAID', totalDue: 50, principalPaid: 50, interestPaid: 0 },
        { status: 'OVERDUE', totalDue: 80, principalPaid: 0, interestPaid: 0 },
      ] as never;
      expect(c.overdueCount).toBe(2);
      expect(c.overdueTotal).toBe(165); // (100-10-5) + (80-0-0) = 85 + 80
    });
  });
});
