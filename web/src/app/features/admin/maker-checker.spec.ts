import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MakerCheckerComponent } from './maker-checker';
import { AdminService, MakerCheckerEntry } from './admin.service';

type Svc = Record<'listMakerChecker' | 'approveMakerChecker' | 'rejectMakerChecker', ReturnType<typeof vi.fn>>;

function entry(over: Partial<MakerCheckerEntry> = {}): MakerCheckerEntry {
  return {
    id: 'mc1', entityType: 'LOAN', actionName: 'APPROVE', madeByUsername: 'maker',
    madeOnDate: '2026-01-01', checkedByUsername: null, checkedOnDate: null,
    status: 'PENDING', resourceId: 'res1', ...over,
  };
}

describe('MakerCheckerComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listMakerChecker: vi.fn().mockReturnValue(of([entry()])),
      approveMakerChecker: vi.fn().mockReturnValue(of(entry({ status: 'APPROVED' }))),
      rejectMakerChecker: vi.fn().mockReturnValue(of(entry({ status: 'REJECTED' }))),
    };
    TestBed.configureTestingModule({
      imports: [MakerCheckerComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(MakerCheckerComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads PENDING entries on init', () => {
    const c = make();
    expect(svc.listMakerChecker).toHaveBeenCalledWith('PENDING');
    expect(c.entries).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('passes undefined when the "all" filter is selected', () => {
    const c = make();
    c.statusFilter = '';
    c.load();
    expect(svc.listMakerChecker).toHaveBeenLastCalledWith(undefined);
  });

  it('flags an error on load failure', () => {
    svc.listMakerChecker.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load entries.');
  });

  describe('confirm', () => {
    it('approves when modal is approve and replaces the entry', () => {
      const c = make();
      c.openApprove(entry());
      c.confirm();
      expect(svc.approveMakerChecker).toHaveBeenCalledWith('mc1');
      expect(c.entries.find(e => e.id === 'mc1')!.status).toBe('APPROVED');
      expect(c.activeModal).toBeNull();
    });
    it('rejects when modal is reject', () => {
      const c = make();
      c.openReject(entry());
      c.confirm();
      expect(svc.rejectMakerChecker).toHaveBeenCalledWith('mc1');
    });
    it('does nothing without a target entry', () => {
      const c = make();
      c.targetEntry = null;
      c.confirm();
      expect(svc.approveMakerChecker).not.toHaveBeenCalled();
    });
    it('surfaces an error on failure', () => {
      svc.approveMakerChecker.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openApprove(entry());
      c.confirm();
      expect(c.modalError).toBe('Action failed.');
      expect(c.modalWorking).toBe(false);
    });
  });

  it('closeModal respects the working flag', () => {
    const c = make();
    c.openApprove(entry());
    c.modalWorking = true;
    c.closeModal();
    expect(c.activeModal).toBe('approve');
    c.modalWorking = false;
    c.closeModal();
    expect(c.activeModal).toBeNull();
    expect(c.targetEntry).toBeNull();
  });

  it('statusVariant maps statuses', () => {
    const c = make();
    expect(c.statusVariant('PENDING')).toBe('warning');
    expect(c.statusVariant('APPROVED')).toBe('success');
    expect(c.statusVariant('REJECTED')).toBe('neutral');
  });
});
