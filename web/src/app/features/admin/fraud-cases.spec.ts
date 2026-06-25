import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FraudCasesComponent } from './fraud-cases';
import { AdminService, FraudCase } from './admin.service';

type Svc = Record<'listFraudCases' | 'createFraudCase' | 'updateFraudCase', ReturnType<typeof vi.fn>>;

function fcase(over: Partial<FraudCase> = {}): FraudCase {
  return {
    id: 'ca1', caseNumber: 'CASE-001', title: 'Case', status: 'OPEN', riskLevel: 'HIGH',
    assignedTo: 'agent', resolutionNotes: 'notes', createdAt: '2026-01-01', ...over,
  };
}
function page(content: FraudCase[], totalElements = content.length) {
  return of({ content, totalElements, totalPages: 1, size: 20, number: 0 });
}

describe('FraudCasesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listFraudCases: vi.fn().mockReturnValue(page([fcase()], 1)),
      createFraudCase: vi.fn().mockReturnValue(of(fcase({ id: 'ca2' }))),
      updateFraudCase: vi.fn().mockReturnValue(of(fcase({ status: 'CLOSED' }))),
    };
    TestBed.configureTestingModule({
      imports: [FraudCasesComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(FraudCasesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads cases on init', () => {
    const c = make();
    expect(svc.listFraudCases).toHaveBeenCalledWith(undefined, undefined, 0, 20);
    expect(c.cases).toHaveLength(1);
    expect(c.total).toBe(1);
    expect(c.loading).toBe(false);
  });

  it('clears loading on load failure', () => {
    svc.listFraudCases.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('applyFilters resets to page 0 and forwards filters', () => {
    const c = make();
    c.page = 2;
    c.filterStatus = 'OPEN';
    c.filterRisk = 'HIGH';
    c.applyFilters();
    expect(c.page).toBe(0);
    expect(svc.listFraudCases).toHaveBeenLastCalledWith('OPEN', 'HIGH', 0, 20);
  });

  it('clearFilters empties the filters', () => {
    const c = make();
    c.filterStatus = 'OPEN'; c.filterRisk = 'HIGH';
    c.clearFilters();
    expect(c.filterStatus).toBe('');
    expect(c.filterRisk).toBe('');
  });

  it('open/closePanel toggle the detail panel', () => {
    const c = make();
    c.openPanel(fcase());
    expect(c.panelOpen).toBe(true);
    c.closePanel();
    expect(c.panelOpen).toBe(false);
    expect(c.selected).toBeNull();
  });

  describe('openEdit / confirmEdit', () => {
    it('primes the edit form from the selected case', () => {
      const c = make();
      c.openPanel(fcase({ status: 'OPEN', assignedTo: 'a1', resolutionNotes: 'n1' }));
      c.openEdit();
      expect(c.editStatus).toBe('OPEN');
      expect(c.editAssignedTo).toBe('a1');
      expect(c.editNotes).toBe('n1');
      expect(c.showEditModal).toBe(true);
    });
    it('openEdit is a no-op without a selection', () => {
      const c = make();
      c.selected = null;
      c.openEdit();
      expect(c.showEditModal).toBe(false);
    });
    it('confirmEdit persists the change and closes the panel', () => {
      const c = make();
      c.openPanel(fcase());
      c.openEdit();
      c.editStatus = 'CLOSED';
      c.confirmEdit();
      expect(svc.updateFraudCase).toHaveBeenCalledWith('ca1', 'CLOSED', 'agent', 'notes');
      expect(c.showEditModal).toBe(false);
      expect(c.panelOpen).toBe(false);
    });
  });

  describe('openCreate / confirmCreate', () => {
    it('creates a case from the new-case form', () => {
      const c = make();
      c.openCreate();
      c.newTitle = 'New Case';
      c.newRisk = 'CRITICAL';
      c.newAssignedTo = 'agent2';
      c.confirmCreate();
      expect(svc.createFraudCase).toHaveBeenCalledWith('New Case', undefined, 'CRITICAL', 'agent2');
      expect(c.showCreateModal).toBe(false);
    });
    it('is a no-op without a title', () => {
      const c = make();
      c.newTitle = '';
      c.confirmCreate();
      expect(svc.createFraudCase).not.toHaveBeenCalled();
    });
  });

  describe('pagination', () => {
    let c: FraudCasesComponent;
    beforeEach(() => {
      svc.listFraudCases.mockReturnValue(page([], 50));
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

  it('statusChip + riskChip map with neutral fallback', () => {
    const c = make();
    expect(c.statusChip('OPEN')).toBe('warning');
    expect(c.statusChip('UNDER_INVESTIGATION')).toBe('info');
    expect(c.statusChip('CLOSED')).toBe('neutral');
    expect(c.statusChip('???')).toBe('neutral');
    expect(c.riskChip('LOW')).toBe('success');
    expect(c.riskChip('CRITICAL')).toBe('critical');
    expect(c.riskChip('???')).toBe('neutral');
  });
});
