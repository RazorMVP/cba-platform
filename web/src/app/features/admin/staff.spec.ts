import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { StaffComponent } from './staff';
import { AdminService, Staff } from './admin.service';

type Svc = Record<'listStaff' | 'listOffices' | 'createStaff' | 'updateStaff', ReturnType<typeof vi.fn>>;

function staff(over: Partial<Staff> = {}): Staff {
  return {
    id: 's1', firstName: 'Jane', lastName: 'Doe', displayName: 'Jane Doe',
    email: 'j@d.com', mobileNo: '123', joiningDate: '2020-01-01',
    loanOfficer: false, active: true, officeId: 'o1', officeName: 'HQ', ...over,
  };
}

describe('StaffComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listStaff: vi.fn().mockReturnValue(of([staff()])),
      listOffices: vi.fn().mockReturnValue(of([{ id: 'o1', name: 'HQ' }])),
      createStaff: vi.fn().mockReturnValue(of(staff({ id: 's2' }))),
      updateStaff: vi.fn().mockReturnValue(of(staff())),
    };
    TestBed.configureTestingModule({
      imports: [StaffComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(StaffComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads offices and staff on init', () => {
    const c = make();
    expect(svc.listOffices).toHaveBeenCalled();
    expect(svc.listStaff).toHaveBeenCalledWith(undefined);
    expect(c.staff).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('flags an error when staff loading fails', () => {
    svc.listStaff.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load staff.');
  });

  it('onOfficeFilter reloads passing the filter office', () => {
    const c = make();
    c.filterOfficeId = 'o9';
    c.onOfficeFilter();
    expect(svc.listStaff).toHaveBeenLastCalledWith('o9');
  });

  it('filteredStaff applies the loan-officer-only toggle', () => {
    const c = make();
    c.staff = [staff({ loanOfficer: true }), staff({ id: 's2', loanOfficer: false })];
    c.loanOfficerOnly = false;
    expect(c.filteredStaff).toHaveLength(2);
    c.loanOfficerOnly = true;
    expect(c.filteredStaff).toHaveLength(1);
  });

  describe('save', () => {
    it('creates new staff in create mode then reloads', () => {
      const c = make();
      c.openCreate();
      c.form = { firstName: 'N', lastName: 'U', email: '', mobileNo: '', joiningDate: '', loanOfficer: false, officeId: 'o1' };
      c.save();
      expect(svc.createStaff).toHaveBeenCalledWith(c.form);
      expect(c.activeModal).toBeNull();
    });
    it('updates in edit mode', () => {
      const c = make();
      c.openEdit(staff());
      c.save();
      expect(svc.updateStaff).toHaveBeenCalledWith('s1', expect.objectContaining({ firstName: 'Jane' }));
    });
    it('surfaces an error on failure and clears working', () => {
      svc.createStaff.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.save();
      expect(c.modalError).toBe('Save failed. Please try again.');
      expect(c.working).toBe(false);
    });
  });

  it('openEdit copies fields with null-safe defaults', () => {
    const c = make();
    c.openEdit(staff({ email: null, mobileNo: null, joiningDate: null }));
    expect(c.form.email).toBe('');
    expect(c.form.mobileNo).toBe('');
    expect(c.form.joiningDate).toBe('');
    expect(c.activeModal).toBe('edit');
  });

  it('officeName resolves the office or echoes the id', () => {
    const c = make();
    c.offices = [{ id: 'o1', name: 'HQ' } as never];
    expect(c.officeName('o1')).toBe('HQ');
    expect(c.officeName('zzz')).toBe('zzz');
  });
});
