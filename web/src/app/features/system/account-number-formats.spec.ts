import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AccountNumberFormatsComponent } from './account-number-formats';
import { SystemService, AccountNumberFormat } from './system.service';

type Svc = Record<
  'listAccountNumberFormats' | 'createAccountNumberFormat' |
  'updateAccountNumberFormat' | 'deleteAccountNumberFormat',
  ReturnType<typeof vi.fn>
>;

function fmt(over: Partial<AccountNumberFormat> = {}): AccountNumberFormat {
  return { id: 'anf1', accountType: 'SAVINGS', prefixType: 'NONE', ...over };
}

describe('AccountNumberFormatsComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listAccountNumberFormats: vi.fn().mockReturnValue(of([fmt()])),
      createAccountNumberFormat: vi.fn().mockReturnValue(of(fmt({ id: 'anf2' }))),
      updateAccountNumberFormat: vi.fn().mockReturnValue(of(fmt({ id: 'anf1' }))),
      deleteAccountNumberFormat: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [AccountNumberFormatsComponent],
      providers: [provideRouter([]), { provide: SystemService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(AccountNumberFormatsComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads formats on init', () => {
    const c = make();
    expect(svc.listAccountNumberFormats).toHaveBeenCalled();
    expect(c.formats).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listAccountNumberFormats.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load account number formats.');
  });

  it('openEdit copies the format into the form', () => {
    const c = make();
    c.openEdit(fmt({ id: 'anf1', accountType: 'LOAN', prefixType: 'ACCOUNT_TYPE' }));
    expect(c.editTarget?.id).toBe('anf1');
    expect(c.form).toEqual({ accountType: 'LOAN', prefixType: 'ACCOUNT_TYPE' });
    expect(c.activeModal).toBe('edit');
  });

  describe('save', () => {
    it('creates when no edit target then reloads', () => {
      const c = make();
      c.openCreate();
      c.form = { accountType: 'CLIENT', prefixType: 'CLIENT_NAME' };
      c.save();
      expect(svc.createAccountNumberFormat).toHaveBeenCalledWith(c.form);
      expect(c.activeModal).toBeNull();
      expect(svc.listAccountNumberFormats).toHaveBeenCalledTimes(2);
    });

    it('updates when an edit target is set', () => {
      const c = make();
      c.openEdit(fmt({ id: 'anf1' }));
      c.save();
      expect(svc.updateAccountNumberFormat).toHaveBeenCalledWith('anf1', c.form);
    });

    it('surfaces an error on failure', () => {
      svc.createAccountNumberFormat.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.save();
      expect(c.modalError).toBe('Save failed. Please try again.');
      expect(c.working).toBe(false);
    });
  });

  describe('confirmDelete', () => {
    it('deletes the target then reloads', () => {
      const c = make();
      c.openDelete(fmt({ id: 'anf1' }));
      c.confirmDelete();
      expect(svc.deleteAccountNumberFormat).toHaveBeenCalledWith('anf1');
      expect(svc.listAccountNumberFormats).toHaveBeenCalledTimes(2);
    });

    it('does nothing without a target', () => {
      const c = make();
      c.deleteTarget = null;
      c.confirmDelete();
      expect(svc.deleteAccountNumberFormat).not.toHaveBeenCalled();
    });
  });

  it('prefixLabel humanises the prefix type', () => {
    const c = make();
    expect(c.prefixLabel('NONE')).toBe('None');
    expect(c.prefixLabel('LOAN_PRODUCT_SHORT_NAME')).toBe('Loan Product Short Name');
    expect(c.prefixLabel('CLIENT_NAME')).toBe('Client Name');
  });
});
