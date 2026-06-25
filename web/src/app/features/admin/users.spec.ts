import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { UsersComponent } from './users';
import { AdminService, PlatformUser } from './admin.service';

type Svc = Record<
  'listUsers' | 'listRoles' | 'listOffices' | 'createUser' | 'deleteUser' | 'enableUser' | 'disableUser',
  ReturnType<typeof vi.fn>
>;

function user(over: Partial<PlatformUser> = {}): PlatformUser {
  return {
    id: 'u1', username: 'jdoe', firstname: 'Jane', lastname: 'Doe', email: 'j@d.com',
    officeId: 'o1', officeName: 'HQ', roles: [{ id: 'r1', name: 'ADMIN' }], enabled: true,
    createdAt: '2026-01-01', ...over,
  };
}

describe('UsersComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listUsers: vi.fn().mockReturnValue(of([user()])),
      listRoles: vi.fn().mockReturnValue(of([{ id: 'r1', name: 'ADMIN' }])),
      listOffices: vi.fn().mockReturnValue(of([{ id: 'o1', name: 'HQ' }])),
      createUser: vi.fn().mockReturnValue(of(user({ id: 'u2', username: 'new' }))),
      deleteUser: vi.fn().mockReturnValue(of(void 0)),
      enableUser: vi.fn().mockReturnValue(of(user({ enabled: true }))),
      disableUser: vi.fn().mockReturnValue(of(user({ enabled: false }))),
    };
    TestBed.configureTestingModule({
      imports: [UsersComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(UsersComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads users, roles and offices on init', () => {
    const c = make();
    expect(svc.listUsers).toHaveBeenCalled();
    expect(svc.listRoles).toHaveBeenCalled();
    expect(svc.listOffices).toHaveBeenCalled();
    expect(c.users).toHaveLength(1);
    expect(c.roles).toHaveLength(1);
    expect(c.offices).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error message when user loading fails', () => {
    svc.listUsers.mockReturnValue(throwError(() => new Error('boom')));
    const c = make();
    expect(c.error).toBe('Failed to load users.');
    expect(c.loading).toBe(false);
  });

  describe('filtered getter', () => {
    it('returns all users when query is empty', () => {
      const c = make();
      expect(c.filtered).toHaveLength(1);
    });
    it('matches on username, email and full name', () => {
      const c = make();
      c.users = [user({ username: 'alpha' }), user({ id: 'u2', username: 'beta', email: 'b@x.com' })];
      c.searchQuery = 'beta';
      expect(c.filtered).toHaveLength(1);
      c.searchQuery = 'b@x';
      expect(c.filtered).toHaveLength(1);
      c.searchQuery = 'Jane Doe';
      expect(c.filtered).toHaveLength(2);
    });
  });

  it('toggleRoleId adds then removes an id', () => {
    const c = make();
    c.toggleRoleId('r1');
    expect(c.selectedRoleIds).toEqual(['r1']);
    c.toggleRoleId('r1');
    expect(c.selectedRoleIds).toEqual([]);
  });

  describe('submitCreate', () => {
    it('does nothing when required fields are missing', () => {
      const c = make();
      c.openCreateModal();
      c.submitCreate();
      expect(svc.createUser).not.toHaveBeenCalled();
    });
    it('creates the user and appends it on success', () => {
      const c = make();
      c.openCreateModal();
      c.form = { username: 'new', firstname: 'N', lastname: 'U', email: 'n@u.com', password: 'pw', officeId: 'o1', roleIds: [] };
      c.selectedRoleIds = ['r1'];
      c.submitCreate();
      expect(svc.createUser).toHaveBeenCalledWith(expect.objectContaining({ username: 'new', roleIds: ['r1'] }));
      expect(c.users).toHaveLength(2);
      expect(c.activeModal).toBeNull();
    });
    it('surfaces an error on failure', () => {
      svc.createUser.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.form = { username: 'new', firstname: 'N', lastname: 'U', email: 'n@u.com', password: 'pw', officeId: 'o1', roleIds: [] };
      c.submitCreate();
      expect(c.modalError).toBe('Failed to create user.');
      expect(c.modalWorking).toBe(false);
    });
  });

  it('submitDelete removes the user', () => {
    const c = make();
    c.openDeleteModal(user());
    c.submitDelete();
    expect(svc.deleteUser).toHaveBeenCalledWith('u1');
    expect(c.users).toHaveLength(0);
    expect(c.activeModal).toBeNull();
  });

  it('toggleEnabled calls disable when enabled, enable when disabled', () => {
    const c = make();
    c.toggleEnabled(user({ enabled: true }));
    expect(svc.disableUser).toHaveBeenCalledWith('u1');
    c.toggleEnabled(user({ enabled: false }));
    expect(svc.enableUser).toHaveBeenCalledWith('u1');
  });

  it('closeModal respects the working flag', () => {
    const c = make();
    c.activeModal = 'create';
    c.modalWorking = true;
    c.closeModal();
    expect(c.activeModal).toBe('create');
    c.modalWorking = false;
    c.closeModal();
    expect(c.activeModal).toBeNull();
  });

  it('roleNames joins names or falls back to a dash', () => {
    const c = make();
    expect(c.roleNames(user({ roles: [{ id: 'r1', name: 'ADMIN' }, { id: 'r2', name: 'TELLER' }] }))).toBe('ADMIN, TELLER');
    expect(c.roleNames(user({ roles: [] }))).toBe('—');
  });
});
