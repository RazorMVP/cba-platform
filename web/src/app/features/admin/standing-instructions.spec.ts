import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { StandingInstructionsComponent } from './standing-instructions';
import { AdminService, StandingInstruction } from './admin.service';

type Svc = Record<
  'listStandingInstructions' | 'createStandingInstruction' | 'updateStandingInstruction' |
  'deleteStandingInstruction' | 'disableStandingInstruction' | 'enableStandingInstruction',
  ReturnType<typeof vi.fn>
>;

function si(over: Partial<StandingInstruction> = {}): StandingInstruction {
  return {
    id: 'si1', name: 'Rent', fromAccountId: 'a1', toAccountId: 'a2',
    instructionType: 'FIXED', priority: 'MEDIUM', status: 'ACTIVE',
    recurrenceType: 'PERIODIC_RECURRENCE', recurrenceFrequency: 1, amount: 100,
    validFrom: '2026-01-01', validTill: '2026-12-31', createdAt: '2026-01-01', ...over,
  };
}
function page(content: StandingInstruction[], totalElements: number) {
  return of({ content, totalElements, totalPages: 1, size: 20, number: 0 });
}

describe('StandingInstructionsComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listStandingInstructions: vi.fn().mockReturnValue(page([si()], 1)),
      createStandingInstruction: vi.fn().mockReturnValue(of(si({ id: 'si2' }))),
      updateStandingInstruction: vi.fn().mockReturnValue(of(si())),
      deleteStandingInstruction: vi.fn().mockReturnValue(of(void 0)),
      disableStandingInstruction: vi.fn().mockReturnValue(of(si({ status: 'DISABLED' }))),
      enableStandingInstruction: vi.fn().mockReturnValue(of(si({ status: 'ACTIVE' }))),
    };
    TestBed.configureTestingModule({
      imports: [StandingInstructionsComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(StandingInstructionsComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads instructions on init', () => {
    const c = make();
    expect(svc.listStandingInstructions).toHaveBeenCalledWith(0);
    expect(c.instructions).toHaveLength(1);
    expect(c.total).toBe(1);
    expect(c.loading).toBe(false);
  });

  it('flags an error on load failure', () => {
    svc.listStandingInstructions.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load standing instructions.');
  });

  describe('save', () => {
    it('creates in create mode then reloads', () => {
      const c = make();
      c.openCreate();
      c.save();
      expect(svc.createStandingInstruction).toHaveBeenCalledWith(c.form);
      expect(c.activeModal).toBeNull();
    });
    it('updates in edit mode', () => {
      const c = make();
      c.openEdit(si());
      c.save();
      expect(svc.updateStandingInstruction).toHaveBeenCalledWith('si1', expect.objectContaining({ name: 'Rent' }));
    });
    it('surfaces an error on failure', () => {
      svc.createStandingInstruction.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.save();
      expect(c.modalError).toBe('Save failed. Please try again.');
      expect(c.working).toBe(false);
    });
  });

  it('openEdit fills the form with null-safe defaults', () => {
    const c = make();
    c.openEdit(si({ recurrenceFrequency: null, amount: null, validFrom: null, validTill: null }));
    expect(c.form.recurrenceFrequency).toBe(1);
    expect(c.form.amount).toBe(0);
    expect(c.form.validFrom).toBe('');
  });

  it('confirmDelete deletes and reloads', () => {
    const c = make();
    c.openDelete(si());
    c.confirmDelete();
    expect(svc.deleteStandingInstruction).toHaveBeenCalledWith('si1');
    expect(c.activeModal).toBeNull();
  });

  it('confirmDelete is a no-op without a target', () => {
    const c = make();
    c.actionTarget = null;
    c.confirmDelete();
    expect(svc.deleteStandingInstruction).not.toHaveBeenCalled();
  });

  describe('confirmToggle', () => {
    it('disables an ACTIVE instruction', () => {
      const c = make();
      c.openToggle(si({ status: 'ACTIVE' }));
      c.confirmToggle();
      expect(svc.disableStandingInstruction).toHaveBeenCalledWith('si1');
    });
    it('enables a non-ACTIVE instruction', () => {
      const c = make();
      c.openToggle(si({ status: 'DISABLED' }));
      c.confirmToggle();
      expect(svc.enableStandingInstruction).toHaveBeenCalledWith('si1');
    });
  });

  it('statusVariant maps statuses', () => {
    const c = make();
    expect(c.statusVariant(si({ status: 'ACTIVE' }))).toBe('success');
    expect(c.statusVariant(si({ status: 'DISABLED' }))).toBe('warning');
    expect(c.statusVariant(si({ status: 'DELETED' }))).toBe('neutral');
  });

  it('priorityClass maps priorities', () => {
    const c = make();
    expect(c.priorityClass('URGENT')).toBe('urgent');
    expect(c.priorityClass('HIGH')).toBe('high');
    expect(c.priorityClass('MEDIUM')).toBe('medium');
    expect(c.priorityClass('LOW')).toBe('low');
  });

  describe('pagination', () => {
    let c: StandingInstructionsComponent;
    beforeEach(() => {
      svc.listStandingInstructions.mockReturnValue(page([], 50));
      c = make();
      c.total = 50;
    });
    it('totalPages computes correctly', () => {
      expect(c.totalPages).toBe(3);
    });
    it('next stops at the last page; prev never below 0', () => {
      c.page = 0; c.next(); expect(c.page).toBe(1);
      c.next(); expect(c.page).toBe(2);
      c.next(); expect(c.page).toBe(2);
      c.prev(); expect(c.page).toBe(1);
      c.page = 0; c.prev(); expect(c.page).toBe(0);
    });
  });
});
