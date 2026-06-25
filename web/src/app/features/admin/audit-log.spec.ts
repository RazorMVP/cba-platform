import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuditLogComponent } from './audit-log';
import { AdminService, AuditLog } from './admin.service';

type Svc = Record<'listAuditLogs', ReturnType<typeof vi.fn>>;

function log(over: Partial<AuditLog> = {}): AuditLog {
  return {
    id: 'a1', entityType: 'LOAN', entityId: 'l1', action: 'CREATE_LOAN',
    changedBy: 'admin', changedAt: '2026-01-01', ...over,
  };
}
function page(content: AuditLog[], totalElements: number, totalPages = 1) {
  return of({ content, totalElements, totalPages, size: 20, number: 0 });
}

describe('AuditLogComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = { listAuditLogs: vi.fn().mockReturnValue(page([log()], 1, 1)) };
    TestBed.configureTestingModule({
      imports: [AuditLogComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(AuditLogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the first page on init', () => {
    const c = make();
    expect(svc.listAuditLogs).toHaveBeenCalledWith(0, {});
    expect(c.rows).toHaveLength(1);
    expect(c.totalElements).toBe(1);
    expect(c.loading).toBe(false);
  });

  it('flags an error on load failure', () => {
    svc.listAuditLogs.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load audit logs.');
  });

  it('applyFilter copies pending filter, resets to page 0 and reloads', () => {
    const c = make();
    c.page = 3;
    c.pendingFilter = { entityType: 'LOAN' };
    c.applyFilter();
    expect(c.filter).toEqual({ entityType: 'LOAN' });
    expect(c.page).toBe(0);
    expect(svc.listAuditLogs).toHaveBeenLastCalledWith(0, { entityType: 'LOAN' });
  });

  it('clearFilter empties both filters', () => {
    const c = make();
    c.pendingFilter = { entityType: 'LOAN' };
    c.filter = { entityType: 'LOAN' };
    c.clearFilter();
    expect(c.pendingFilter).toEqual({});
    expect(c.filter).toEqual({});
  });

  describe('pagination', () => {
    // nextPage() calls load(), which re-reads totalPages from the service, so the
    // mock must echo totalPages=3 to keep the upper bound stable across reloads.
    it('nextPage advances within bounds', () => {
      svc.listAuditLogs.mockReturnValue(page([], 50, 3));
      const c = make();
      c.totalPages = 3;
      c.page = 0;
      c.nextPage(); expect(c.page).toBe(1);
      c.nextPage(); expect(c.page).toBe(2);
      c.nextPage(); expect(c.page).toBe(2);
    });
    it('prevPage decrements but never below 0', () => {
      const c = make();
      c.page = 1;
      c.prevPage(); expect(c.page).toBe(0);
      c.prevPage(); expect(c.page).toBe(0);
    });
    it('fromIndex/toIndex compute the visible window', () => {
      const c = make();
      c.page = 0; c.totalElements = 45;
      expect(c.fromIndex()).toBe(1);
      expect(c.toIndex()).toBe(20);
      c.page = 2;
      expect(c.fromIndex()).toBe(41);
      expect(c.toIndex()).toBe(45);
    });
  });

  it('openDetail/closeDetail toggle the selected row', () => {
    const c = make();
    c.openDetail(log());
    expect(c.selected).not.toBeNull();
    c.closeDetail();
    expect(c.selected).toBeNull();
  });

  it('prettyJson formats valid JSON and echoes invalid strings', () => {
    const c = make();
    expect(c.prettyJson('{"a":1}')).toBe('{\n  "a": 1\n}');
    expect(c.prettyJson('not-json')).toBe('not-json');
    expect(c.prettyJson()).toBe('');
  });

  it('actionVariant maps action prefixes', () => {
    const c = make();
    expect(c.actionVariant('CREATE_LOAN')).toBe('info');
    expect(c.actionVariant('APPROVE_X')).toBe('success');
    expect(c.actionVariant('DELETE_X')).toBe('error');
    expect(c.actionVariant('UPDATE_X')).toBe('warning');
    expect(c.actionVariant('SOMETHING')).toBe('neutral');
  });

  it('entityVariant maps entity types', () => {
    const c = make();
    expect(c.entityVariant('CUSTOMER')).toBe('primary');
    expect(c.entityVariant('LOAN')).toBe('warning');
    expect(c.entityVariant('GL_ACCOUNT')).toBe('info');
    expect(c.entityVariant('PAYMENT')).toBe('success');
    expect(c.entityVariant('USER')).toBe('neutral');
    expect(c.entityVariant('UNKNOWN')).toBe('neutral');
  });
});
