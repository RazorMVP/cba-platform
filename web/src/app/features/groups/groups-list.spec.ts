import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { GroupsListComponent } from './groups-list';
import { GroupsService, Group } from './groups.service';
import { AdminService } from '../admin/admin.service';

type Svc = Record<'listGroups' | 'createGroup', ReturnType<typeof vi.fn>>;

function group(over: Partial<Group> = {}): Group {
  return {
    id: 'g1', name: 'Alpha Group', externalId: null, centerId: null, centerName: null,
    officeId: 'o1', officeName: 'HQ', staffId: null, staffName: null,
    status: 'ACTIVE', activationDate: '2026-01-01', memberCount: 4, ...over,
  };
}

describe('GroupsListComponent', () => {
  let svc: Svc;
  let admin: Record<'listOffices', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    svc = {
      listGroups: vi.fn().mockReturnValue(of([group()])),
      createGroup: vi.fn().mockReturnValue(of(group({ id: 'g2', name: 'New Group' }))),
    };
    admin = { listOffices: vi.fn().mockReturnValue(of([{ id: 'o1', name: 'HQ' }])) };
    TestBed.configureTestingModule({
      imports: [GroupsListComponent],
      providers: [
        provideRouter([]),
        { provide: GroupsService, useValue: svc },
        { provide: AdminService, useValue: admin },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(GroupsListComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads groups + offices on init', () => {
    const c = make();
    expect(svc.listGroups).toHaveBeenCalled();
    expect(admin.listOffices).toHaveBeenCalled();
    expect(c.groups).toHaveLength(1);
    expect(c.offices).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listGroups.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load groups.');
    expect(c.loading).toBe(false);
  });

  it('filtered narrows by status and name', () => {
    const c = make();
    c.groups = [
      group({ id: 'a', name: 'Alpha', status: 'ACTIVE' }),
      group({ id: 'b', name: 'Beta', status: 'PENDING' }),
    ];
    c.statusFilter = 'PENDING';
    expect(c.filtered).toHaveLength(1);
    expect(c.filtered[0].name).toBe('Beta');
    c.statusFilter = '';
    c.searchQuery = 'alp';
    expect(c.filtered).toHaveLength(1);
    expect(c.filtered[0].name).toBe('Alpha');
  });

  it('statusVariant maps status to a badge variant', () => {
    const c = make();
    expect(c.statusVariant('ACTIVE')).toBe('success');
    expect(c.statusVariant('PENDING')).toBe('warning');
    expect(c.statusVariant('CLOSED')).toBe('neutral');
  });

  describe('create modal', () => {
    it('openCreateModal resets the form', () => {
      const c = make();
      c.form = { name: 'X', officeId: 'o9' };
      c.openCreateModal();
      expect(c.activeModal).toBe('create');
      expect(c.form).toEqual({ name: '', officeId: '' });
    });

    it('submitCreate is a no-op when name/office missing', () => {
      const c = make();
      c.form = { name: '', officeId: '' };
      c.submitCreate();
      expect(svc.createGroup).not.toHaveBeenCalled();
    });

    it('submitCreate appends the new group and closes', () => {
      const c = make();
      c.openCreateModal();
      c.form = { name: 'New Group', officeId: 'o1' };
      c.submitCreate();
      expect(svc.createGroup).toHaveBeenCalledWith({ name: 'New Group', officeId: 'o1' });
      expect(c.groups).toHaveLength(2);
      expect(c.activeModal).toBeNull();
    });

    it('submitCreate surfaces an error on failure', () => {
      svc.createGroup.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.form = { name: 'New Group', officeId: 'o1' };
      c.submitCreate();
      expect(c.modalError).toBe('Failed to create group.');
      expect(c.modalWorking).toBe(false);
    });

    it('closeModal does nothing while working', () => {
      const c = make();
      c.openCreateModal();
      c.modalWorking = true;
      c.closeModal();
      expect(c.activeModal).toBe('create');
    });
  });
});
