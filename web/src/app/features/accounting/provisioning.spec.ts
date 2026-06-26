import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ProvisioningComponent } from './provisioning';
import {
  AccountingService, ProvisioningCriteria, ProvisioningDefinition, GlAccount,
} from './accounting.service';

type Svc = Record<
  'listProvisioningCriteria' | 'createProvisioningCriteria' |
  'updateProvisioningCriteria' | 'deleteProvisioningCriteria' | 'listGlAccounts',
  ReturnType<typeof vi.fn>
>;

function def(over: Partial<ProvisioningDefinition> = {}): ProvisioningDefinition {
  return {
    categoryName: 'STANDARD', minAge: 0, maxAge: 30, provisionPercentage: 1,
    liabilityAccountId: 'gl-l', expenseAccountId: 'gl-e', ...over,
  };
}

function criteria(over: Partial<ProvisioningCriteria> = {}): ProvisioningCriteria {
  return { id: 'pc1', criteriaName: 'IFRS9', definitions: [def()], ...over };
}

function acc(over: Partial<GlAccount> = {}): GlAccount {
  return {
    id: 'gl-l', glCode: '3001', name: 'Provision Liability', accountType: 'LIABILITY', usage: 'DETAIL',
    manualEntriesAllowed: true, disabled: false, ...over,
  };
}

describe('ProvisioningComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listProvisioningCriteria: vi.fn().mockReturnValue(of([criteria()])),
      createProvisioningCriteria: vi.fn().mockReturnValue(of(criteria({ id: 'pc2', criteriaName: 'New' }))),
      updateProvisioningCriteria: vi.fn().mockReturnValue(of(criteria({ id: 'pc1', criteriaName: 'Edited' }))),
      deleteProvisioningCriteria: vi.fn().mockReturnValue(of(void 0)),
      listGlAccounts: vi.fn().mockReturnValue(of([
        acc({ id: 'gl-l', accountType: 'LIABILITY' }),
        acc({ id: 'gl-e', accountType: 'EXPENSE', glCode: '6001', name: 'Loan Loss Expense' }),
      ])),
    };
    TestBed.configureTestingModule({
      imports: [ProvisioningComponent],
      providers: [provideRouter([]), { provide: AccountingService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(ProvisioningComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads criteria and GL accounts on init', () => {
    const c = make();
    expect(svc.listProvisioningCriteria).toHaveBeenCalled();
    expect(svc.listGlAccounts).toHaveBeenCalled();
    expect(c.criteria).toHaveLength(1);
    expect(c.glAccounts).toHaveLength(2);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listProvisioningCriteria.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load provisioning criteria.');
    expect(c.loading).toBe(false);
  });

  describe('GL dropdown getters', () => {
    it('liabilityAccounts and expenseAccounts filter by type/usage/enabled', () => {
      const c = make();
      c.glAccounts = [
        acc({ id: 'l1', accountType: 'LIABILITY', usage: 'DETAIL' }),
        acc({ id: 'l2', accountType: 'LIABILITY', usage: 'HEADER' }),
        acc({ id: 'l3', accountType: 'LIABILITY', disabled: true }),
        acc({ id: 'e1', accountType: 'EXPENSE', usage: 'DETAIL' }),
      ];
      expect(c.liabilityAccounts.map(a => a.id)).toEqual(['l1']);
      expect(c.expenseAccounts.map(a => a.id)).toEqual(['e1']);
    });
  });

  describe('helpers', () => {
    it('definitionCount returns the number of definitions', () => {
      const c = make();
      expect(c.definitionCount(criteria({ definitions: [def(), def()] }))).toBe(2);
      expect(c.definitionCount(criteria({ definitions: undefined as unknown as ProvisioningDefinition[] }))).toBe(0);
    });

    it('glAccountLabel resolves a label or shows a dash for empty', () => {
      const c = make();
      c.glAccounts = [acc({ id: 'gl-l', glCode: '3001', name: 'Provision Liability' })];
      expect(c.glAccountLabel('gl-l')).toBe('3001 Provision Liability');
      expect(c.glAccountLabel('')).toBe('—');
    });
  });

  describe('openCreateModal', () => {
    it('seeds the five default IFRS 9 age bands', () => {
      const c = make();
      c.openCreateModal();
      expect(c.formDefinitions).toHaveLength(5);
      expect(c.formDefinitions.map(d => d.categoryName)).toEqual(
        ['STANDARD', 'WATCH', 'SUB_STANDARD', 'DOUBTFUL', 'LOSS'],
      );
      expect(c.activeModal).toBe('create');
    });
  });

  it('addDefinition and removeDefinition mutate the form definitions', () => {
    const c = make();
    c.openCreateModal();
    const before = c.formDefinitions.length;
    c.addDefinition();
    expect(c.formDefinitions).toHaveLength(before + 1);
    c.removeDefinition(0);
    expect(c.formDefinitions).toHaveLength(before);
  });

  describe('submitModal', () => {
    it('does nothing without a name or definitions', () => {
      const c = make();
      c.openCreateModal();
      c.formName = '';
      c.submitModal();
      expect(svc.createProvisioningCriteria).not.toHaveBeenCalled();
    });

    it('creates criteria and appends it', () => {
      const c = make();
      c.openCreateModal();
      c.formName = 'New';
      c.submitModal();
      expect(svc.createProvisioningCriteria).toHaveBeenCalledWith(
        expect.objectContaining({ criteriaName: 'New' }),
      );
      expect(c.criteria).toHaveLength(2);
      expect(c.activeModal).toBeNull();
    });

    it('updates criteria in place when editing', () => {
      const c = make();
      c.criteria = [criteria({ id: 'pc1', criteriaName: 'IFRS9' })];
      c.openEditModal(criteria({ id: 'pc1' }));
      c.formName = 'Edited';
      c.submitModal();
      expect(svc.updateProvisioningCriteria).toHaveBeenCalledWith('pc1', expect.anything());
      expect(c.criteria[0].criteriaName).toBe('Edited');
    });

    it('surfaces an error on failure', () => {
      svc.createProvisioningCriteria.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreateModal();
      c.formName = 'New';
      c.submitModal();
      expect(c.modalError).toBe('Failed to save criteria.');
      expect(c.modalWorking).toBe(false);
    });
  });

  describe('submitDelete', () => {
    it('removes the criteria from the list', () => {
      const c = make();
      c.criteria = [criteria({ id: 'pc1' })];
      c.openDeleteModal(criteria({ id: 'pc1' }));
      c.submitDelete();
      expect(svc.deleteProvisioningCriteria).toHaveBeenCalledWith('pc1');
      expect(c.criteria).toHaveLength(0);
      expect(c.activeModal).toBeNull();
    });

    it('surfaces an error on failure', () => {
      svc.deleteProvisioningCriteria.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openDeleteModal(criteria({ id: 'pc1' }));
      c.submitDelete();
      expect(c.modalError).toBe('Cannot delete criteria in use.');
      expect(c.modalWorking).toBe(false);
    });
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
});
