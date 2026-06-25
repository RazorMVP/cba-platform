import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OpenBankingComponent } from './open-banking';
import { AdminService, TppRegistration } from './admin.service';

type Svc = Record<'listTpps' | 'registerTpp' | 'activateTpp' | 'revokeTpp', ReturnType<typeof vi.fn>>;

function tpp(over: Partial<TppRegistration> = {}): TppRegistration {
  return {
    id: 'tp1', name: 'Acme', clientId: 'client-1', country: 'GB',
    allowedScopes: ['accounts'], certificateExpiry: null, status: 'PENDING',
    registeredAt: '2026-01-01', ...over,
  };
}

describe('OpenBankingComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listTpps: vi.fn().mockReturnValue(of([tpp()])),
      registerTpp: vi.fn().mockReturnValue(of(tpp({ id: 'tp2', name: 'New' }))),
      activateTpp: vi.fn().mockReturnValue(of(tpp({ status: 'ACTIVE' }))),
      revokeTpp: vi.fn().mockReturnValue(of(tpp({ status: 'REVOKED' }))),
    };
    TestBed.configureTestingModule({
      imports: [OpenBankingComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(OpenBankingComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads TPPs on init', () => {
    const c = make();
    expect(svc.listTpps).toHaveBeenCalled();
    expect(c.tpps).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('flags an error on load failure', () => {
    svc.listTpps.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load TPP registrations.');
  });

  it('toggleScope adds then removes a scope', () => {
    const c = make();
    c.openCreateModal();
    c.toggleScope('payments');
    expect(c.form.allowedScopes).toEqual(['payments']);
    c.toggleScope('payments');
    expect(c.form.allowedScopes).toEqual([]);
  });

  describe('submitCreate', () => {
    it('does nothing without name or clientId', () => {
      const c = make();
      c.openCreateModal();
      c.submitCreate();
      expect(svc.registerTpp).not.toHaveBeenCalled();
    });
    it('registers and appends the TPP on success', () => {
      const c = make();
      c.openCreateModal();
      c.form = { name: 'New', clientId: 'c2', country: 'KE', allowedScopes: ['openid'] };
      c.submitCreate();
      expect(svc.registerTpp).toHaveBeenCalledWith(c.form);
      expect(c.tpps).toHaveLength(2);
      expect(c.activeModal).toBeNull();
    });
    it('surfaces an error on failure', () => {
      svc.registerTpp.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreateModal();
      c.form = { name: 'New', clientId: 'c2', country: 'KE', allowedScopes: [] };
      c.submitCreate();
      expect(c.modalError).toBe('Failed to register TPP.');
    });
  });

  it('submitDelete revokes and replaces the TPP', () => {
    const c = make();
    c.openDeleteModal(tpp());
    c.submitDelete();
    expect(svc.revokeTpp).toHaveBeenCalledWith('tp1');
    expect(c.tpps.find(t => t.id === 'tp1')!.status).toBe('REVOKED');
    expect(c.activeModal).toBeNull();
  });

  it('activateTpp replaces the TPP in the list', () => {
    const c = make();
    c.activateTpp(tpp());
    expect(svc.activateTpp).toHaveBeenCalledWith('tp1');
    expect(c.tpps.find(t => t.id === 'tp1')!.status).toBe('ACTIVE');
  });

  it('statusVariant maps statuses', () => {
    const c = make();
    expect(c.statusVariant('ACTIVE')).toBe('success');
    expect(c.statusVariant('REVOKED')).toBe('neutral');
    expect(c.statusVariant('PENDING')).toBe('warning');
  });
});
