import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { GlAccountsComponent } from './gl-accounts';
import { AccountingService, GlAccount } from './accounting.service';

type Svc = Record<
  'listGlAccounts' | 'createGlAccount' | 'updateGlAccount' | 'enableGlAccount' | 'disableGlAccount',
  ReturnType<typeof vi.fn>
>;

function acc(over: Partial<GlAccount> = {}): GlAccount {
  return {
    id: 'gl1', glCode: '1001', name: 'Cash', accountType: 'ASSET', usage: 'DETAIL',
    manualEntriesAllowed: true, disabled: false, ...over,
  };
}

describe('GlAccountsComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listGlAccounts: vi.fn().mockReturnValue(of([acc()])),
      createGlAccount: vi.fn().mockReturnValue(of(acc({ id: 'gl2', glCode: '2001', name: 'Loans' }))),
      updateGlAccount: vi.fn().mockReturnValue(of(acc({ id: 'gl1', name: 'Cash Edited' }))),
      enableGlAccount: vi.fn().mockReturnValue(of(acc({ id: 'gl1', disabled: false }))),
      disableGlAccount: vi.fn().mockReturnValue(of(acc({ id: 'gl1', disabled: true }))),
    };
    TestBed.configureTestingModule({
      imports: [GlAccountsComponent],
      providers: [provideRouter([]), { provide: AccountingService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(GlAccountsComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads accounts on init and applies the filter', () => {
    const c = make();
    expect(svc.listGlAccounts).toHaveBeenCalled();
    expect(c.accounts).toHaveLength(1);
    expect(c.filtered).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listGlAccounts.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load GL accounts.');
    expect(c.loading).toBe(false);
  });

  describe('applyFilter', () => {
    it('hides disabled accounts unless showDisabled is set', () => {
      const c = make();
      c.accounts = [acc({ id: 'a' }), acc({ id: 'b', disabled: true })];
      c.applyFilter();
      expect(c.filtered).toHaveLength(1);
      c.showDisabled = true;
      c.applyFilter();
      expect(c.filtered).toHaveLength(2);
    });

    it('narrows by type filter', () => {
      const c = make();
      c.accounts = [acc({ id: 'a', accountType: 'ASSET' }), acc({ id: 'b', accountType: 'INCOME' })];
      c.typeFilter = 'INCOME';
      c.applyFilter();
      expect(c.filtered).toHaveLength(1);
      expect(c.filtered[0].accountType).toBe('INCOME');
    });

    it('matches code or name case-insensitively', () => {
      const c = make();
      c.accounts = [acc({ id: 'a', glCode: '1001', name: 'Cash' }), acc({ id: 'b', glCode: '5001', name: 'Wages' })];
      c.searchQuery = 'wag';
      c.applyFilter();
      expect(c.filtered).toHaveLength(1);
      expect(c.filtered[0].name).toBe('Wages');
    });
  });

  it('countByType ignores disabled accounts', () => {
    const c = make();
    c.accounts = [
      acc({ id: 'a', accountType: 'ASSET' }),
      acc({ id: 'b', accountType: 'ASSET', disabled: true }),
      acc({ id: 'c', accountType: 'INCOME' }),
    ];
    expect(c.countByType('ASSET')).toBe(1);
    expect(c.countByType('INCOME')).toBe(1);
  });

  it('headerAccounts returns only enabled HEADER accounts', () => {
    const c = make();
    c.accounts = [
      acc({ id: 'h1', usage: 'HEADER' }),
      acc({ id: 'h2', usage: 'HEADER', disabled: true }),
      acc({ id: 'd1', usage: 'DETAIL' }),
    ];
    expect(c.headerAccounts.map(a => a.id)).toEqual(['h1']);
  });

  describe('toggleDisable', () => {
    it('disables an enabled account', () => {
      const c = make();
      c.accounts = [acc({ id: 'gl1', disabled: false })];
      c.toggleDisable(acc({ id: 'gl1', disabled: false }));
      expect(svc.disableGlAccount).toHaveBeenCalledWith('gl1');
      expect(c.accounts[0].disabled).toBe(true);
    });

    it('enables a disabled account', () => {
      const c = make();
      c.accounts = [acc({ id: 'gl1', disabled: true })];
      c.toggleDisable(acc({ id: 'gl1', disabled: true }));
      expect(svc.enableGlAccount).toHaveBeenCalledWith('gl1');
      expect(c.accounts[0].disabled).toBe(false);
    });
  });

  describe('submitModal', () => {
    it('does nothing without a code or name', () => {
      const c = make();
      c.openCreateModal();
      c.form.glCode = '';
      c.submitModal();
      expect(svc.createGlAccount).not.toHaveBeenCalled();
    });

    it('creates an account and appends it', () => {
      const c = make();
      c.openCreateModal();
      c.form = { glCode: '2001', name: 'Loans', accountType: 'ASSET', usage: 'DETAIL', manualEntriesAllowed: true, description: '', parentId: '' };
      c.submitModal();
      expect(svc.createGlAccount).toHaveBeenCalledWith(
        expect.objectContaining({ glCode: '2001', name: 'Loans', parentId: undefined, description: undefined }),
      );
      expect(c.accounts).toHaveLength(2);
      expect(c.activeModal).toBeNull();
    });

    it('updates an account in place', () => {
      const c = make();
      c.accounts = [acc({ id: 'gl1', name: 'Cash' })];
      c.openEditModal(acc({ id: 'gl1', name: 'Cash' }));
      c.submitModal();
      expect(svc.updateGlAccount).toHaveBeenCalledWith('gl1', expect.anything());
      expect(c.accounts[0].name).toBe('Cash Edited');
    });

    it('surfaces an error on failure', () => {
      svc.createGlAccount.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreateModal();
      c.form = { glCode: '2001', name: 'Loans', accountType: 'ASSET', usage: 'DETAIL', manualEntriesAllowed: true, description: '', parentId: '' };
      c.submitModal();
      expect(c.modalError).toBe('Failed to save GL account.');
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

  it('typeVariant maps each account type to a badge variant', () => {
    const c = make();
    expect(c.typeVariant('ASSET')).toBe('success');
    expect(c.typeVariant('LIABILITY')).toBe('warning');
    expect(c.typeVariant('EQUITY')).toBe('info');
    expect(c.typeVariant('EXPENSE')).toBe('neutral');
  });
});
