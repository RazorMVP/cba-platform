import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RolesComponent } from './roles';
import { AdminService, Role, Permission } from './admin.service';

type Svc = Record<
  'listRoles' | 'listPermissions' | 'createRole' | 'updateRole' | 'updateRolePermissions' | 'deleteRole',
  ReturnType<typeof vi.fn>
>;

function perm(over: Partial<Permission> = {}): Permission {
  return { id: 'p1', grouping: 'loan', code: 'LOAN_READ', entityName: 'LOAN', actionName: 'READ', canMakerChecker: false, ...over };
}
function role(over: Partial<Role> = {}): Role {
  return { id: 'r1', name: 'ADMIN', description: 'admins', disabled: false, permissions: [perm()], ...over };
}

describe('RolesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listRoles: vi.fn().mockReturnValue(of([role()])),
      listPermissions: vi.fn().mockReturnValue(of([perm(), perm({ id: 'p2', grouping: 'account', code: 'ACC_READ' })])),
      createRole: vi.fn().mockReturnValue(of(role({ id: 'r2', name: 'NEW' }))),
      updateRole: vi.fn().mockReturnValue(of(role({ name: 'EDITED' }))),
      updateRolePermissions: vi.fn().mockReturnValue(of(role({ permissions: [perm(), perm({ id: 'p2' })] }))),
      deleteRole: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [RolesComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(RolesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads roles and permissions on init', () => {
    const c = make();
    expect(svc.listRoles).toHaveBeenCalled();
    expect(svc.listPermissions).toHaveBeenCalled();
    expect(c.roles).toHaveLength(1);
    expect(c.allPerms).toHaveLength(2);
    expect(c.loading).toBe(false);
  });

  it('flags an error when role loading fails', () => {
    svc.listRoles.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load roles.');
  });

  describe('permission grouping helpers', () => {
    it('permGroups returns unique sorted groups', () => {
      const c = make();
      expect(c.permGroups).toEqual(['account', 'loan']);
    });
    it('permsByGroup filters by grouping', () => {
      const c = make();
      expect(c.permsByGroup('loan')).toHaveLength(1);
    });
    it('groupAllSelected reflects selection state', () => {
      const c = make();
      expect(c.groupAllSelected('loan')).toBe(false);
      c.selectedPermIds = ['p1'];
      expect(c.groupAllSelected('loan')).toBe(true);
    });
  });

  it('togglePermId toggles ids', () => {
    const c = make();
    c.togglePermId('p1');
    expect(c.selectedPermIds).toEqual(['p1']);
    c.togglePermId('p1');
    expect(c.selectedPermIds).toEqual([]);
  });

  it('toggleGroup adds then clears all ids in a group', () => {
    const c = make();
    c.toggleGroup('loan', true);
    expect(c.selectedPermIds).toContain('p1');
    c.toggleGroup('loan', false);
    expect(c.selectedPermIds).not.toContain('p1');
  });

  describe('submitRole', () => {
    it('creates a role in create mode', () => {
      const c = make();
      c.openCreateModal();
      c.formName = 'NEW';
      c.submitRole();
      expect(svc.createRole).toHaveBeenCalledWith({ name: 'NEW', description: '' });
      expect(c.roles).toHaveLength(2);
    });
    it('updates a role in edit mode', () => {
      const c = make();
      c.openEditModal(role());
      c.formName = 'EDITED';
      c.submitRole();
      expect(svc.updateRole).toHaveBeenCalledWith('r1', { name: 'EDITED', description: 'admins' });
      expect(c.roles.find(r => r.id === 'r1')!.name).toBe('EDITED');
    });
    it('does nothing when name is blank', () => {
      const c = make();
      c.openCreateModal();
      c.formName = '';
      c.submitRole();
      expect(svc.createRole).not.toHaveBeenCalled();
    });
    it('shows an error on failure', () => {
      svc.createRole.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreateModal();
      c.formName = 'NEW';
      c.submitRole();
      expect(c.modalError).toBe('Failed to save role.');
    });
  });

  it('submitPermissions PUTs the selected ids and replaces the role', () => {
    const c = make();
    c.openPermissionsModal(role());
    c.selectedPermIds = ['p1', 'p2'];
    c.submitPermissions();
    expect(svc.updateRolePermissions).toHaveBeenCalledWith('r1', { permissionIds: ['p1', 'p2'] });
    expect(c.activeModal).toBeNull();
  });

  it('submitDelete removes the role', () => {
    const c = make();
    c.openDeleteModal(role());
    c.submitDelete();
    expect(svc.deleteRole).toHaveBeenCalledWith('r1');
    expect(c.roles).toHaveLength(0);
  });

  it('permCount counts permissions safely', () => {
    const c = make();
    expect(c.permCount(role({ permissions: [perm(), perm({ id: 'p2' })] }))).toBe(2);
    expect(c.permCount({ ...role(), permissions: undefined as never })).toBe(0);
  });
});
