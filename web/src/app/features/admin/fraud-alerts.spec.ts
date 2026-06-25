import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FraudAlertsComponent } from './fraud-alerts';
import { AdminService, FraudAlert, FraudCase } from './admin.service';

type Svc = Record<
  'listFraudAlerts' | 'listFraudCases' | 'reviewFraudAlert' | 'closeFraudAlert' |
  'linkAlertToCase' | 'createFraudCase',
  ReturnType<typeof vi.fn>
>;

function alert(over: Partial<FraudAlert> = {}): FraudAlert {
  return { id: 'al1', severity: 'HIGH', status: 'OPEN', alertType: 'VELOCITY', customerId: 'cu1', createdAt: '2026-01-01', ...over };
}
function fcase(over: Partial<FraudCase> = {}): FraudCase {
  return { id: 'ca1', caseNumber: 'CASE-001', title: 'Case', status: 'OPEN', riskLevel: 'HIGH', createdAt: '2026-01-01', ...over };
}
function page<T>(content: T[], totalElements = content.length) {
  return of({ content, totalElements, totalPages: 1, size: 20, number: 0 });
}

describe('FraudAlertsComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listFraudAlerts: vi.fn().mockReturnValue(page([alert()], 1)),
      listFraudCases: vi.fn().mockReturnValue(page([fcase()])),
      reviewFraudAlert: vi.fn().mockReturnValue(of(alert({ status: 'REVIEWING' }))),
      closeFraudAlert: vi.fn().mockReturnValue(of(alert({ status: 'CLOSED_CONFIRMED' }))),
      linkAlertToCase: vi.fn().mockReturnValue(of(fcase())),
      createFraudCase: vi.fn().mockReturnValue(of(fcase({ id: 'ca2' }))),
    };
    TestBed.configureTestingModule({
      imports: [FraudAlertsComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(FraudAlertsComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads alerts and cases on init', () => {
    const c = make();
    expect(svc.listFraudAlerts).toHaveBeenCalledWith(undefined, undefined, 0, 20);
    expect(svc.listFraudCases).toHaveBeenCalledWith(undefined, undefined, 0, 100);
    expect(c.alerts).toHaveLength(1);
    expect(c.cases).toHaveLength(1);
    expect(c.total).toBe(1);
    expect(c.loading).toBe(false);
  });

  it('clears loading on alert load failure', () => {
    svc.listFraudAlerts.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('applyFilters resets to page 0 and forwards filters', () => {
    const c = make();
    c.page = 2;
    c.filterStatus = 'OPEN';
    c.filterSeverity = 'HIGH';
    c.applyFilters();
    expect(c.page).toBe(0);
    expect(svc.listFraudAlerts).toHaveBeenLastCalledWith('OPEN', 'HIGH', 0, 20);
  });

  it('clearFilters empties the filters and reloads', () => {
    const c = make();
    c.filterStatus = 'OPEN'; c.filterSeverity = 'HIGH';
    c.clearFilters();
    expect(c.filterStatus).toBe('');
    expect(c.filterSeverity).toBe('');
  });

  it('open/closePanel toggle the detail panel', () => {
    const c = make();
    c.openPanel(alert());
    expect(c.panelOpen).toBe(true);
    expect(c.selected).not.toBeNull();
    c.closePanel();
    expect(c.panelOpen).toBe(false);
    expect(c.selected).toBeNull();
  });

  describe('confirmReview', () => {
    it('reviews the selected alert and closes the panel', () => {
      const c = make();
      c.openPanel(alert());
      c.reviewedBy = 'admin';
      c.confirmReview();
      expect(svc.reviewFraudAlert).toHaveBeenCalledWith('al1', 'admin');
      expect(c.showReviewModal).toBe(false);
      expect(c.panelOpen).toBe(false);
    });
    it('is a no-op without a selection', () => {
      const c = make();
      c.selected = null;
      c.confirmReview();
      expect(svc.reviewFraudAlert).not.toHaveBeenCalled();
    });
  });

  it('confirmClose closes the alert with status and reviewer', () => {
    const c = make();
    c.openPanel(alert());
    c.openClose();
    c.closeStatus = 'CLOSED_CONFIRMED';
    c.reviewedBy = 'admin';
    c.confirmClose();
    expect(svc.closeFraudAlert).toHaveBeenCalledWith('al1', 'CLOSED_CONFIRMED', 'admin');
    expect(c.showCloseModal).toBe(false);
  });

  describe('confirmLink', () => {
    it('links the alert to a case', () => {
      const c = make();
      c.openPanel(alert());
      c.selectedCaseId = 'ca1';
      c.confirmLink();
      expect(svc.linkAlertToCase).toHaveBeenCalledWith('al1', 'ca1');
      expect(c.showLinkModal).toBe(false);
    });
    it('is a no-op without a selected case', () => {
      const c = make();
      c.openPanel(alert());
      c.selectedCaseId = '';
      c.confirmLink();
      expect(svc.linkAlertToCase).not.toHaveBeenCalled();
    });
  });

  describe('confirmCreateCase', () => {
    it('creates a case using the selected alert customer', () => {
      const c = make();
      c.openPanel(alert({ customerId: 'cu9' }));
      c.newCaseTitle = 'Investigate';
      c.newCaseRisk = 'CRITICAL';
      c.confirmCreateCase();
      expect(svc.createFraudCase).toHaveBeenCalledWith('Investigate', 'cu9', 'CRITICAL', '');
      expect(c.showCreateCaseModal).toBe(false);
    });
    it('is a no-op without a title', () => {
      const c = make();
      c.newCaseTitle = '';
      c.confirmCreateCase();
      expect(svc.createFraudCase).not.toHaveBeenCalled();
    });
  });

  describe('pagination', () => {
    let c: FraudAlertsComponent;
    beforeEach(() => {
      svc.listFraudAlerts.mockReturnValue(page([], 50));
      c = make();
      c.total = 50;
    });
    it('nextPage stops at the last page', () => {
      c.page = 0; c.nextPage(); expect(c.page).toBe(1);
      c.nextPage(); expect(c.page).toBe(2);
      c.nextPage(); expect(c.page).toBe(2);
    });
    it('prevPage never below 0', () => {
      c.page = 1; c.prevPage(); expect(c.page).toBe(0);
      c.prevPage(); expect(c.page).toBe(0);
    });
    it('totalPages divides by page size', () => {
      expect(c.totalPages()).toBe(3);
    });
  });

  it('severityChip + statusChip map variants with neutral fallback', () => {
    const c = make();
    expect(c.severityChip('HIGH')).toBe('error');
    expect(c.severityChip('CRITICAL')).toBe('critical');
    expect(c.severityChip('???')).toBe('neutral');
    expect(c.statusChip('OPEN')).toBe('warning');
    expect(c.statusChip('CLOSED_CONFIRMED')).toBe('error');
    expect(c.statusChip('???')).toBe('neutral');
  });
});
