import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { GlClosuresComponent } from './gl-closures';
import { AccountingService, GlClosure } from './accounting.service';
import { AdminService, Office } from '../admin/admin.service';

type Svc = Record<'listClosures' | 'createClosure', ReturnType<typeof vi.fn>>;
type Admin = Record<'listOffices', ReturnType<typeof vi.fn>>;

function office(over: Partial<Office> = {}): Office {
  return {
    id: 'o1', name: 'HQ', externalId: '', openingDate: '2026-01-01',
    parentId: null, parentName: null, hierarchy: '.', ...over,
  };
}

function closure(over: Partial<GlClosure> = {}): GlClosure {
  return { id: 'cl1', officeId: 'o1', officeName: 'HQ', closingDate: '2026-05-31', ...over };
}

describe('GlClosuresComponent', () => {
  let svc: Svc;
  let admin: Admin;

  beforeEach(() => {
    svc = {
      listClosures: vi.fn().mockReturnValue(of([closure()])),
      createClosure: vi.fn().mockReturnValue(of(closure({ id: 'cl2', closingDate: '2026-06-30' }))),
    };
    admin = { listOffices: vi.fn().mockReturnValue(of([office()])) };
    TestBed.configureTestingModule({
      imports: [GlClosuresComponent],
      providers: [
        provideRouter([]),
        { provide: AccountingService, useValue: svc },
        { provide: AdminService, useValue: admin },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(GlClosuresComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads offices on init, selects the first and loads its closures', () => {
    const c = make();
    expect(admin.listOffices).toHaveBeenCalled();
    expect(c.selectedOfficeId).toBe('o1');
    expect(svc.listClosures).toHaveBeenCalledWith('o1');
    expect(c.closures).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('does not load closures when there are no offices', () => {
    admin.listOffices.mockReturnValue(of([]));
    const c = make();
    expect(c.selectedOfficeId).toBe('');
    expect(svc.listClosures).not.toHaveBeenCalled();
  });

  it('sets an error when closure loading fails', () => {
    svc.listClosures.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load GL closures.');
    expect(c.loading).toBe(false);
  });

  it('onOfficeChange clears and reloads', () => {
    const c = make();
    c.selectedOfficeId = 'o2';
    c.onOfficeChange();
    expect(svc.listClosures).toHaveBeenLastCalledWith('o2');
  });

  it('openCreate pre-fills the form with the selected office and today', () => {
    const c = make();
    c.openCreate();
    expect(c.form.officeId).toBe('o1');
    expect(c.form.closingDate).toBeTruthy();
    expect(c.showCreateModal).toBe(true);
  });

  describe('submitCreate', () => {
    it('rejects a form missing office or date', () => {
      const c = make();
      c.form = { officeId: '', closingDate: '', comments: '' };
      c.submitCreate();
      expect(c.saveError).toBe('Office and closing date are required.');
      expect(svc.createClosure).not.toHaveBeenCalled();
    });

    it('creates a closure and prepends it', () => {
      const c = make();
      c.form = { officeId: 'o1', closingDate: '2026-06-30', comments: 'EOY' };
      c.submitCreate();
      expect(svc.createClosure).toHaveBeenCalledWith({ officeId: 'o1', closingDate: '2026-06-30', comments: 'EOY' });
      expect(c.closures).toHaveLength(2);
      expect(c.closures[0].id).toBe('cl2');
      expect(c.showCreateModal).toBe(false);
      expect(c.saving).toBe(false);
    });

    it('surfaces an error on failure', () => {
      svc.createClosure.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.form = { officeId: 'o1', closingDate: '2026-06-30', comments: '' };
      c.submitCreate();
      expect(c.saveError).toContain('Failed to create closure');
      expect(c.saving).toBe(false);
    });
  });

  it('selectedOfficeName resolves the chosen office name', () => {
    const c = make();
    c.offices = [office({ id: 'o1', name: 'HQ' }), office({ id: 'o2', name: 'Branch' })];
    c.selectedOfficeId = 'o2';
    expect(c.selectedOfficeName()).toBe('Branch');
    c.selectedOfficeId = 'missing';
    expect(c.selectedOfficeName()).toBe('');
  });
});
