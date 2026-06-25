import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AccountAlgorithmsComponent } from './account-algorithms';
import { SystemService, TenantAlgorithmConfig } from './system.service';
import { AdminService, Tenant } from '../admin/admin.service';

type Svc = Record<'getAlgorithmConfig' | 'updateAlgorithmConfig', ReturnType<typeof vi.fn>>;
type TenantSvc = Record<'listTenants', ReturnType<typeof vi.fn>>;

function tenant(over: Partial<Tenant> = {}): Tenant {
  return { id: 't1', code: 'CBA_NG', name: 'CBA Nigeria', currencyCode: 'NGN', countryCode: 'NG', localeCode: 'en', ...over };
}
function config(over: Partial<TenantAlgorithmConfig> = {}): TenantAlgorithmConfig {
  return { bankCode: '058', validationMode: 'STRICT', algorithms: { SAVINGS: 'NUBAN' }, ...over };
}

describe('AccountAlgorithmsComponent', () => {
  let svc: Svc;
  let tenantSvc: TenantSvc;

  beforeEach(() => {
    svc = {
      getAlgorithmConfig: vi.fn().mockReturnValue(of(config())),
      updateAlgorithmConfig: vi.fn().mockReturnValue(of(config({ bankCode: '999' }))),
    };
    tenantSvc = { listTenants: vi.fn().mockReturnValue(of([tenant()])) };
    TestBed.configureTestingModule({
      imports: [AccountAlgorithmsComponent],
      providers: [
        provideRouter([]),
        { provide: SystemService, useValue: svc },
        { provide: AdminService, useValue: tenantSvc },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(AccountAlgorithmsComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads tenants then per-tenant configs on init', () => {
    const c = make();
    expect(tenantSvc.listTenants).toHaveBeenCalled();
    expect(svc.getAlgorithmConfig).toHaveBeenCalledWith('t1');
    expect(c.tenants).toHaveLength(1);
    expect(c.configFor('t1')?.bankCode).toBe('058');
    expect(c.loading).toBe(false);
  });

  it('sets an error when tenants fail to load', () => {
    tenantSvc.listTenants.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load tenants.');
    expect(c.loading).toBe(false);
  });

  it('algorithmFor falls back to MIFOS when unset', () => {
    const c = make();
    expect(c.algorithmFor('t1', 'SAVINGS')).toBe('NUBAN');
    expect(c.algorithmFor('t1', 'LOAN')).toBe('MIFOS');
    expect(c.algorithmFor('unknown', 'SAVINGS')).toBe('MIFOS');
  });

  describe('startEdit', () => {
    it('seeds the form from the stored config', () => {
      const c = make();
      c.startEdit(tenant({ id: 't1' }));
      expect(c.editingTenantId).toBe('t1');
      expect(c.editForm.bankCode).toBe('058');
      expect(c.editForm.algorithms['SAVINGS']).toBe('NUBAN');
    });

    it('defaults all account types to MIFOS when no config exists', () => {
      const c = make();
      c.configs.clear();
      c.startEdit(tenant({ id: 't1' }));
      expect(c.editForm.validationMode).toBe('STRICT');
      expect(c.editForm.algorithms['LOAN']).toBe('MIFOS');
    });
  });

  it('cancelEdit clears the editing tenant', () => {
    const c = make();
    c.editingTenantId = 't1';
    c.cancelEdit();
    expect(c.editingTenantId).toBe('');
  });

  it('setAlgorithm updates a single account type', () => {
    const c = make();
    c.startEdit(tenant({ id: 't1' }));
    c.setAlgorithm('LOAN', 'NUBAN');
    expect(c.editForm.algorithms['LOAN']).toBe('NUBAN');
  });

  it('nubanSelected reflects whether any type uses NUBAN', () => {
    const c = make();
    c.editForm.algorithms = { SAVINGS: 'MIFOS' };
    expect(c.nubanSelected).toBe(false);
    c.editForm.algorithms = { SAVINGS: 'NUBAN' };
    expect(c.nubanSelected).toBe(true);
  });

  describe('saveEdit', () => {
    it('blocks saving when NUBAN is chosen but no bank code is set', () => {
      const c = make();
      c.editForm = { bankCode: '', validationMode: 'STRICT', algorithms: { SAVINGS: 'NUBAN' } };
      c.saveEdit(tenant({ id: 't1' }));
      expect(svc.updateAlgorithmConfig).not.toHaveBeenCalled();
      expect(c.editError).toContain('Bank code is required');
    });

    it('saves and stores the updated config', () => {
      const c = make();
      c.editForm = { bankCode: '999', validationMode: 'STRICT', algorithms: { SAVINGS: 'NUBAN' } };
      c.saveEdit(tenant({ id: 't1' }));
      expect(svc.updateAlgorithmConfig).toHaveBeenCalledWith('t1', c.editForm);
      expect(c.configFor('t1')?.bankCode).toBe('999');
      expect(c.editingTenantId).toBe('');
    });

    it('surfaces a backend error message', () => {
      svc.updateAlgorithmConfig.mockReturnValue(
        throwError(() => ({ error: { errors: [{ message: 'Bad bank code' }] } })),
      );
      const c = make();
      c.editForm = { bankCode: '058', validationMode: 'STRICT', algorithms: { SAVINGS: 'MIFOS' } };
      c.saveEdit(tenant({ id: 't1' }));
      expect(c.editError).toBe('Bad bank code');
      expect(c.editWorking).toBe(false);
    });
  });

  it('algorithmBadgeClass distinguishes NUBAN from MIFOS', () => {
    const c = make();
    expect(c.algorithmBadgeClass('NUBAN')).toBe('badge-nuban');
    expect(c.algorithmBadgeClass('MIFOS')).toBe('badge-mifos');
  });
});
