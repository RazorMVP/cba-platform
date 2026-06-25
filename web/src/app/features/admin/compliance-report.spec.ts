import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ComplianceReportComponent } from './compliance-report';
import { AdminService } from './admin.service';

type Svc = Record<
  'complianceAuditSummary' | 'complianceFailedLogins' | 'complianceUserActivity' | 'complianceDataAccess',
  ReturnType<typeof vi.fn>
>;

describe('ComplianceReportComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      complianceAuditSummary: vi.fn().mockReturnValue(of([{ action: 'CREATE', entity_type: 'LOAN', event_count: 1, unique_actors: 1 }])),
      complianceFailedLogins: vi.fn().mockReturnValue(of([{ username: 'x', ip_address: '1', status: 'FAILURE', attempt_count: 2, last_attempt: 'd' }])),
      complianceUserActivity: vi.fn().mockReturnValue(of([{ user_id: 'u1', total_actions: 5, entity_types_touched: 2, last_action: 'd' }])),
      complianceDataAccess: vi.fn().mockReturnValue(of([{ entity_id: 'e1', action: 'READ', changed_by: 'a', changed_at: 'd' }])),
    };
    TestBed.configureTestingModule({
      imports: [ComplianceReportComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(ComplianceReportComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the audit summary on init', () => {
    const c = make();
    expect(svc.complianceAuditSummary).toHaveBeenCalledWith(30);
    expect(c.auditRows).toHaveLength(1);
    expect(c.auditLoaded).toBe(true);
    expect(c.auditLoading).toBe(false);
  });

  it('flags an error when the audit summary fails', () => {
    svc.complianceAuditSummary.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.auditError).toBe('Failed to load.');
  });

  describe('switchTab lazy-loads each tab once', () => {
    it('failed-logins', () => {
      const c = make();
      c.switchTab('failed-logins');
      c.switchTab('audit');
      c.switchTab('failed-logins');
      expect(svc.complianceFailedLogins).toHaveBeenCalledTimes(1);
      expect(c.failedRows).toHaveLength(1);
    });
    it('user-activity', () => {
      const c = make();
      c.switchTab('user-activity');
      expect(svc.complianceUserActivity).toHaveBeenCalledTimes(1);
      expect(c.activityRows).toHaveLength(1);
    });
    it('data-access passes the entity type', () => {
      const c = make();
      c.entityType = 'CUSTOMER';
      c.switchTab('data-access');
      expect(svc.complianceDataAccess).toHaveBeenCalledWith(30, 'CUSTOMER');
      expect(c.accessRows).toHaveLength(1);
    });
  });

  it('reload re-fetches the active tab', () => {
    const c = make();
    expect(svc.complianceAuditSummary).toHaveBeenCalledTimes(1);
    c.reload();
    expect(svc.complianceAuditSummary).toHaveBeenCalledTimes(2);
    expect(c.auditLoaded).toBe(true);
  });

  it('reload of data-access tab re-fetches it', () => {
    const c = make();
    c.switchTab('data-access');
    expect(svc.complianceDataAccess).toHaveBeenCalledTimes(1);
    c.reload();
    expect(svc.complianceDataAccess).toHaveBeenCalledTimes(2);
  });
});
