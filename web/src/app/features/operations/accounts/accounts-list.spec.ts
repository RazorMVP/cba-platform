import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AccountsListComponent } from './accounts-list';
import { AccountService } from './account.service';

describe('AccountsListComponent', () => {
  let svc: { list: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    svc = {
      list: vi.fn().mockReturnValue(
        of({ content: [{ id: 'a1', accountType: 'SAVINGS', status: 'ACTIVE' }], totalElements: 1, totalPages: 1, size: 20, number: 0 }),
      ),
    };
    TestBed.configureTestingModule({
      imports: [AccountsListComponent],
      providers: [provideRouter([]), { provide: AccountService, useValue: svc }],
    });
  });

  it('loads the first page on init', () => {
    const fixture = TestBed.createComponent(AccountsListComponent);
    fixture.detectChanges();
    expect(svc.list).toHaveBeenCalledWith(0, 20);
    expect(fixture.componentInstance.accounts).toHaveLength(1);
  });

  it('clears loading on list error', () => {
    svc.list.mockReturnValue(throwError(() => new Error('x')));
    const fixture = TestBed.createComponent(AccountsListComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.loading).toBe(false);
  });

  it('paginates within bounds', () => {
    // loadPage() (called by next/prevPage) re-reads totalElements from the service,
    // so the mock must echo the same total to keep the bounds stable.
    svc.list.mockReturnValue(of({ content: [], totalElements: 50, totalPages: 3, size: 20, number: 0 }));
    const c = TestBed.createComponent(AccountsListComponent).componentInstance;
    c.totalElements = 50;
    c.page = 0; c.nextPage(); expect(c.page).toBe(1);
    c.page = 2; c.nextPage(); expect(c.page).toBe(2); // capped
    c.page = 0; c.prevPage(); expect(c.page).toBe(0); // floored
    expect(c.totalPages).toBe(3);
  });

  it('maps status + type to badge variants and icons', () => {
    const c = TestBed.createComponent(AccountsListComponent).componentInstance;
    expect(c.statusVariant('ACTIVE')).toBe('success');
    expect(c.statusVariant('DORMANT')).toBe('warning');
    expect(c.statusVariant('FROZEN')).toBe('error');
    expect(c.statusVariant('CLOSED')).toBe('neutral');
    expect(c.typeIcon('SAVINGS')).toBe('savings');
    expect(c.typeIcon('CHECKING')).toBe('account_balance_wallet');
    expect(c.typeIcon('FIXED_DEPOSIT')).toBe('lock_clock');
  });
});
