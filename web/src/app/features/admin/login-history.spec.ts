import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginHistoryComponent } from './login-history';
import { AdminService, LoginHistoryEvent, LoginEventSummary } from './admin.service';

type Svc = Record<'loginEventSummary' | 'listLoginEvents', ReturnType<typeof vi.fn>>;

function event(over: Partial<LoginHistoryEvent> = {}): LoginHistoryEvent {
  return {
    id: 'e1', userId: 'u1', username: 'jdoe', ipAddress: '127.0.0.1',
    userAgent: 'UA', status: 'SUCCESS', createdAt: '2026-01-01', ...over,
  };
}
const summary: LoginEventSummary = {
  periodDays: 30, successLogins: 10, failedLogins: 2, lockedAccounts: 1,
  uniqueUsers: 5, topFailedUsers: [{ username: 'bad', failureCount: 3 }],
};
function page(content: LoginHistoryEvent[], totalElements: number) {
  return of({ content, totalElements, totalPages: 1, size: 20, number: 0 });
}

describe('LoginHistoryComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      loginEventSummary: vi.fn().mockReturnValue(of(summary)),
      listLoginEvents: vi.fn().mockReturnValue(page([event()], 1)),
    };
    TestBed.configureTestingModule({
      imports: [LoginHistoryComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(LoginHistoryComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads summary and events on init', () => {
    const c = make();
    expect(svc.loginEventSummary).toHaveBeenCalledWith(30);
    expect(svc.listLoginEvents).toHaveBeenCalled();
    expect(c.summary).toEqual(summary);
    expect(c.events).toHaveLength(1);
    expect(c.totalItems).toBe(1);
    expect(c.loading).toBe(false);
  });

  it('flags an error when events fail to load', () => {
    svc.listLoginEvents.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load login history.');
  });

  it('applyFilter resets to page 0 and forwards filters', () => {
    const c = make();
    c.page = 3;
    c.filterStatus = 'FAILURE';
    c.filterUsername = 'jdoe';
    c.applyFilter();
    expect(c.page).toBe(0);
    expect(svc.listLoginEvents).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 0, status: 'FAILURE', username: 'jdoe' }),
    );
  });

  it('resetFilter clears every filter field', () => {
    const c = make();
    c.filterStatus = 'X'; c.filterUsername = 'Y'; c.filterFrom = 'F'; c.filterTo = 'T';
    c.resetFilter();
    expect(c.filterStatus).toBe('');
    expect(c.filterUsername).toBe('');
    expect(c.filterFrom).toBe('');
    expect(c.filterTo).toBe('');
    expect(c.page).toBe(0);
  });

  describe('pagination', () => {
    let c: LoginHistoryComponent;
    beforeEach(() => {
      svc.listLoginEvents.mockReturnValue(page([], 50));
      c = make();
      c.totalItems = 50;
    });
    it('nextPage stops at the last page', () => {
      c.page = 0; c.nextPage(); expect(c.page).toBe(1);
      c.nextPage(); expect(c.page).toBe(2);
      c.nextPage(); expect(c.page).toBe(2);
    });
    it('prevPage never below 0', () => {
      c.page = 1; c.prevPage(); expect(c.page).toBe(0);
      c.prevPage(); expect(c.page).toBe(0);
    });
    it('totalPages divides by page size', () => {
      expect(c.totalPages()).toBe(3);
    });
  });

  it('statusVariant maps statuses', () => {
    const c = make();
    expect(c.statusVariant('SUCCESS')).toBe('success');
    expect(c.statusVariant('FAILURE')).toBe('error');
    expect(c.statusVariant('LOCKED')).toBe('warning');
    expect(c.statusVariant('LOGOUT')).toBe('neutral');
  });
});
