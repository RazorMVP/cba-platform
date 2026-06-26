import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CentersListComponent } from './centers-list';
import { GroupsService, Center } from './groups.service';
import { AdminService } from '../admin/admin.service';

type Svc = Record<'listCenters' | 'createCenter', ReturnType<typeof vi.fn>>;

function center(over: Partial<Center> = {}): Center {
  return {
    id: 'ct1', name: 'North Center', externalId: null, officeId: 'o1', officeName: 'HQ',
    staffId: null, staffName: null, status: 'ACTIVE', activationDate: '2026-01-01',
    groupCount: 3, ...over,
  };
}

describe('CentersListComponent', () => {
  let svc: Svc;
  let admin: Record<'listOffices', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    svc = {
      listCenters: vi.fn().mockReturnValue(of([center()])),
      createCenter: vi.fn().mockReturnValue(of(center({ id: 'ct2', name: 'South Center' }))),
    };
    admin = { listOffices: vi.fn().mockReturnValue(of([{ id: 'o1', name: 'HQ' }])) };
    TestBed.configureTestingModule({
      imports: [CentersListComponent],
      providers: [
        provideRouter([]),
        { provide: GroupsService, useValue: svc },
        { provide: AdminService, useValue: admin },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(CentersListComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads centers + offices on init', () => {
    const c = make();
    expect(svc.listCenters).toHaveBeenCalled();
    expect(admin.listOffices).toHaveBeenCalled();
    expect(c.centers).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listCenters.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load centers.');
    expect(c.loading).toBe(false);
  });

  it('filtered narrows by status and name', () => {
    const c = make();
    c.centers = [
      center({ id: 'a', name: 'North', status: 'ACTIVE' }),
      center({ id: 'b', name: 'South', status: 'PENDING' }),
    ];
    c.statusFilter = 'ACTIVE';
    expect(c.filtered).toHaveLength(1);
    expect(c.filtered[0].name).toBe('North');
    c.statusFilter = '';
    c.searchQuery = 'sou';
    expect(c.filtered).toHaveLength(1);
    expect(c.filtered[0].name).toBe('South');
  });

  it('statusVariant maps status to a badge variant', () => {
    const c = make();
    expect(c.statusVariant('ACTIVE')).toBe('success');
    expect(c.statusVariant('PENDING')).toBe('warning');
    expect(c.statusVariant('CLOSED')).toBe('neutral');
  });

  describe('create modal', () => {
    it('submitCreate is a no-op when name/office missing', () => {
      const c = make();
      c.form = { name: '', officeId: '' };
      c.submitCreate();
      expect(svc.createCenter).not.toHaveBeenCalled();
    });

    it('submitCreate appends the new center and closes', () => {
      const c = make();
      c.openCreateModal();
      c.form = { name: 'South Center', officeId: 'o1' };
      c.submitCreate();
      expect(svc.createCenter).toHaveBeenCalledWith({ name: 'South Center', officeId: 'o1' });
      expect(c.centers).toHaveLength(2);
      expect(c.activeModal).toBeNull();
    });

    it('submitCreate surfaces an error on failure', () => {
      svc.createCenter.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.form = { name: 'South Center', officeId: 'o1' };
      c.submitCreate();
      expect(c.modalError).toBe('Failed to create center.');
      expect(c.modalWorking).toBe(false);
    });
  });
});
