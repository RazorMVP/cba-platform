import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AccountingRulesComponent } from './accounting-rules';
import { AccountingService, AccountingRule, GlAccount } from './accounting.service';

type Svc = Record<
  'listAccountingRules' | 'listGlAccounts' | 'createAccountingRule' |
  'updateAccountingRule' | 'deleteAccountingRule',
  ReturnType<typeof vi.fn>
>;

function rule(over: Partial<AccountingRule> = {}): AccountingRule {
  return {
    id: 'ar1', name: 'Cash Sale', description: 'd', debitAccountId: 'gl1', creditAccountId: 'gl2',
    allowMultipleDebits: false, allowMultipleCredits: false, active: true, ...over,
  };
}

function acc(over: Partial<GlAccount> = {}): GlAccount {
  return {
    id: 'gl1', glCode: '1001', name: 'Cash', accountType: 'ASSET', usage: 'DETAIL',
    manualEntriesAllowed: true, disabled: false, ...over,
  };
}

function page(content: AccountingRule[], totalElements = content.length) {
  return of({ content, totalElements, totalPages: Math.ceil(totalElements / 20), size: 20, number: 0 });
}

describe('AccountingRulesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listAccountingRules: vi.fn().mockReturnValue(page([rule()])),
      listGlAccounts: vi.fn().mockReturnValue(of([acc(), acc({ id: 'gl2', glCode: '2001', name: 'Sales' })])),
      createAccountingRule: vi.fn().mockReturnValue(of(rule({ id: 'ar2' }))),
      updateAccountingRule: vi.fn().mockReturnValue(of(rule({ id: 'ar1' }))),
      deleteAccountingRule: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [AccountingRulesComponent],
      providers: [provideRouter([]), { provide: AccountingService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(AccountingRulesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads rules and GL accounts on init', () => {
    const c = make();
    expect(svc.listAccountingRules).toHaveBeenCalledWith(0);
    expect(svc.listGlAccounts).toHaveBeenCalled();
    expect(c.rules).toHaveLength(1);
    expect(c.glAccounts).toHaveLength(2);
    expect(c.total).toBe(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listAccountingRules.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load accounting rules.');
    expect(c.loading).toBe(false);
  });

  it('glLabel resolves a GL account or returns the id', () => {
    const c = make();
    expect(c.glLabel('gl2')).toBe('2001 — Sales');
    expect(c.glLabel('missing')).toBe('missing');
  });

  describe('pagination', () => {
    it('next advances and reloads when more pages exist', () => {
      svc.listAccountingRules.mockReturnValue(page([rule()], 45));
      const c = make();
      expect(c.totalPages).toBe(3);
      c.next();
      expect(c.page).toBe(1);
      expect(svc.listAccountingRules).toHaveBeenLastCalledWith(1);
    });

    it('prev does nothing on the first page', () => {
      const c = make();
      c.prev();
      expect(c.page).toBe(0);
    });

    it('next does nothing on the last page', () => {
      const c = make();
      c.page = 0;
      c.total = 1;
      c.next();
      expect(c.page).toBe(0);
    });
  });

  describe('openEdit', () => {
    it('copies the rule into the form', () => {
      const c = make();
      c.openEdit(rule({ id: 'ar1', name: 'Edited', allowMultipleDebits: true }));
      expect(c.editTarget?.id).toBe('ar1');
      expect(c.form.name).toBe('Edited');
      expect(c.form.allowMultipleDebits).toBe(true);
      expect(c.activeModal).toBe('edit');
    });
  });

  describe('save', () => {
    it('creates when the modal is create', () => {
      const c = make();
      c.openCreate();
      c.form = { name: 'New', description: '', debitAccountId: 'gl1', creditAccountId: 'gl2', allowMultipleDebits: false, allowMultipleCredits: false, active: true };
      c.save();
      expect(svc.createAccountingRule).toHaveBeenCalledWith(c.form);
      expect(c.activeModal).toBeNull();
      expect(svc.listAccountingRules).toHaveBeenCalledTimes(2);
    });

    it('updates when the modal is edit', () => {
      const c = make();
      c.openEdit(rule({ id: 'ar1' }));
      c.save();
      expect(svc.updateAccountingRule).toHaveBeenCalledWith('ar1', c.form);
      expect(c.activeModal).toBeNull();
    });

    it('surfaces an error on failure', () => {
      svc.createAccountingRule.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.save();
      expect(c.modalError).toBe('Save failed. Please try again.');
      expect(c.working).toBe(false);
    });
  });

  describe('confirmDelete', () => {
    it('deletes the target and reloads', () => {
      const c = make();
      c.openDelete(rule({ id: 'ar1' }));
      c.confirmDelete();
      expect(svc.deleteAccountingRule).toHaveBeenCalledWith('ar1');
      expect(c.activeModal).toBeNull();
      expect(svc.listAccountingRules).toHaveBeenCalledTimes(2);
    });

    it('does nothing without a target', () => {
      const c = make();
      c.deleteTarget = null;
      c.confirmDelete();
      expect(svc.deleteAccountingRule).not.toHaveBeenCalled();
    });

    it('keeps working false-safe on error', () => {
      svc.deleteAccountingRule.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openDelete(rule({ id: 'ar1' }));
      c.confirmDelete();
      expect(c.working).toBe(false);
    });
  });
});
