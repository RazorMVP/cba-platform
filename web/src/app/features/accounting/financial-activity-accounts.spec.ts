import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FinancialActivityAccountsComponent } from './financial-activity-accounts';
import { AccountingService, FinancialActivityAccount, GlAccount } from './accounting.service';

type Svc = Record<
  'listFinancialActivityAccounts' | 'createFinancialActivityAccount' |
  'updateFinancialActivityAccount' | 'deleteFinancialActivityAccount' | 'listGlAccounts',
  ReturnType<typeof vi.fn>
>;

function faa(over: Partial<FinancialActivityAccount> = {}): FinancialActivityAccount {
  return {
    id: 'fa1', financialActivity: 'INCOME_INTEREST', glAccountId: 'gl1',
    glCode: '4001', glAccountName: 'Interest Income', glAccountType: 'INCOME', ...over,
  };
}

function acc(over: Partial<GlAccount> = {}): GlAccount {
  return {
    id: 'gl1', glCode: '4001', name: 'Interest Income', accountType: 'INCOME', usage: 'DETAIL',
    manualEntriesAllowed: true, disabled: false, ...over,
  };
}

describe('FinancialActivityAccountsComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listFinancialActivityAccounts: vi.fn().mockReturnValue(of([faa()])),
      createFinancialActivityAccount: vi.fn().mockReturnValue(of(faa({ id: 'fa2' }))),
      updateFinancialActivityAccount: vi.fn().mockReturnValue(of(faa({ id: 'fa1' }))),
      deleteFinancialActivityAccount: vi.fn().mockReturnValue(of(void 0)),
      listGlAccounts: vi.fn().mockReturnValue(of([acc()])),
    };
    TestBed.configureTestingModule({
      imports: [FinancialActivityAccountsComponent],
      providers: [provideRouter([]), { provide: AccountingService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(FinancialActivityAccountsComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads mappings and DETAIL GL accounts on init', () => {
    const c = make();
    expect(svc.listFinancialActivityAccounts).toHaveBeenCalled();
    expect(svc.listGlAccounts).toHaveBeenCalledWith({ usage: 'DETAIL' });
    expect(c.items).toHaveLength(1);
    expect(c.glAccounts).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listFinancialActivityAccounts.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load financial activity accounts.');
    expect(c.loading).toBe(false);
  });

  describe('label helpers', () => {
    it('activityLabel maps known activities and falls back to the raw value', () => {
      const c = make();
      expect(c.activityLabel('INCOME_INTEREST')).toBe('Income — Interest');
      expect(c.activityLabel('UNKNOWN_ACTIVITY')).toBe('UNKNOWN_ACTIVITY');
    });

    it('glAccountLabel resolves code/name or returns the id', () => {
      const c = make();
      c.glAccounts = [{ id: 'gl1', glCode: '4001', name: 'Interest Income' }];
      expect(c.glAccountLabel('gl1')).toBe('4001 — Interest Income');
      expect(c.glAccountLabel('missing')).toBe('missing');
    });
  });

  describe('open modals', () => {
    it('openCreate resets the form to defaults', () => {
      const c = make();
      c.openCreate();
      expect(c.editingId).toBeNull();
      expect(c.form).toEqual({ financialActivity: 'ASSET_FUND_SOURCE', glAccountId: '' });
      expect(c.showModal).toBe(true);
    });

    it('openEdit copies the item into the form', () => {
      const c = make();
      c.openEdit(faa({ id: 'fa1', financialActivity: 'INCOME_FEE', glAccountId: 'glx' }));
      expect(c.editingId).toBe('fa1');
      expect(c.form).toEqual({ financialActivity: 'INCOME_FEE', glAccountId: 'glx' });
      expect(c.showModal).toBe(true);
    });
  });

  describe('save', () => {
    it('creates a new mapping and reloads', () => {
      const c = make();
      c.openCreate();
      c.form = { financialActivity: 'INCOME_FEE', glAccountId: 'gl1' };
      c.save();
      expect(svc.createFinancialActivityAccount).toHaveBeenCalledWith({ financialActivity: 'INCOME_FEE', glAccountId: 'gl1' });
      expect(c.showModal).toBe(false);
      expect(svc.listFinancialActivityAccounts).toHaveBeenCalledTimes(2);
    });

    it('updates an existing mapping when editing', () => {
      const c = make();
      c.openEdit(faa({ id: 'fa1' }));
      c.save();
      expect(svc.updateFinancialActivityAccount).toHaveBeenCalledWith('fa1', c.form);
      expect(c.showModal).toBe(false);
    });

    it('surfaces an error on failure', () => {
      svc.createFinancialActivityAccount.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.save();
      expect(c.modalError).toContain('Save failed');
      expect(c.working).toBe(false);
    });
  });

  describe('remove', () => {
    it('deletes and reloads when confirmed', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      const c = make();
      c.remove('fa1');
      expect(svc.deleteFinancialActivityAccount).toHaveBeenCalledWith('fa1');
      expect(svc.listFinancialActivityAccounts).toHaveBeenCalledTimes(2);
    });

    it('does nothing when cancelled', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      const c = make();
      c.remove('fa1');
      expect(svc.deleteFinancialActivityAccount).not.toHaveBeenCalled();
    });
  });

  it('closeModal respects the working flag', () => {
    const c = make();
    c.showModal = true;
    c.working = true;
    c.closeModal();
    expect(c.showModal).toBe(true);
    c.working = false;
    c.closeModal();
    expect(c.showModal).toBe(false);
  });
});
