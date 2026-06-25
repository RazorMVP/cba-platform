import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FundsComponent } from './funds';
import { SystemService, Fund } from './system.service';

type Svc = Record<'listFunds' | 'createFund' | 'updateFund', ReturnType<typeof vi.fn>>;

function fund(over: Partial<Fund> = {}): Fund {
  return { id: 'f1', name: 'Loan Fund', externalId: 'EXT-1', ...over };
}

describe('FundsComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listFunds: vi.fn().mockReturnValue(of([fund()])),
      createFund: vi.fn().mockReturnValue(of(fund({ id: 'f2' }))),
      updateFund: vi.fn().mockReturnValue(of(fund({ id: 'f1' }))),
    };
    TestBed.configureTestingModule({
      imports: [FundsComponent],
      providers: [provideRouter([]), { provide: SystemService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(FundsComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads funds on init', () => {
    const c = make();
    expect(svc.listFunds).toHaveBeenCalled();
    expect(c.funds).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listFunds.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load funds.');
  });

  it('openEdit seeds the form, coalescing a null externalId', () => {
    const c = make();
    c.openEdit(fund({ id: 'f1', name: 'X', externalId: null }));
    expect(c.editTarget?.id).toBe('f1');
    expect(c.form).toEqual({ name: 'X', externalId: '' });
    expect(c.activeModal).toBe('edit');
  });

  describe('save', () => {
    it('creates when no edit target then reloads and closes', () => {
      const c = make();
      c.openCreate();
      c.form = { name: 'New Fund', externalId: 'E2' };
      c.save();
      expect(svc.createFund).toHaveBeenCalledWith(c.form);
      expect(c.activeModal).toBeNull();
      expect(svc.listFunds).toHaveBeenCalledTimes(2);
    });

    it('updates when an edit target is set', () => {
      const c = make();
      c.openEdit(fund({ id: 'f1' }));
      c.save();
      expect(svc.updateFund).toHaveBeenCalledWith('f1', c.form);
    });

    it('surfaces an error on failure', () => {
      svc.createFund.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.save();
      expect(c.modalError).toBe('Save failed. Please try again.');
      expect(c.working).toBe(false);
    });
  });
});
