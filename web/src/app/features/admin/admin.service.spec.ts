import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { AdminService } from './admin.service';

describe('AdminService', () => {
  let service: AdminService;
  let api: Record<'get' | 'getPage' | 'post' | 'put' | 'delete' | 'command' | 'postForm', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of([])),
      getPage: vi.fn().mockReturnValue(of({ content: [] })),
      post: vi.fn().mockReturnValue(of({})),
      put: vi.fn().mockReturnValue(of({})),
      delete: vi.fn().mockReturnValue(of({})),
      command: vi.fn().mockReturnValue(of({})),
      postForm: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({
      providers: [AdminService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(AdminService);
  });

  it('users enable/disable use the command pattern', () => {
    service.enableUser('u1').subscribe();
    expect(api.command).toHaveBeenCalledWith('/users/u1', 'enable');
    service.disableUser('u1').subscribe();
    expect(api.command).toHaveBeenCalledWith('/users/u1', 'disable');
  });

  it('role permissions update PUTs the permission ids', () => {
    service.updateRolePermissions('r1', { permissionIds: ['p1', 'p2'] }).subscribe();
    expect(api.put).toHaveBeenCalledWith('/roles/r1/permissions', { permissionIds: ['p1', 'p2'] });
  });

  it('maker-checker + TPP + SMS + standing-instruction commands route correctly', () => {
    service.approveMakerChecker('mc1').subscribe();
    expect(api.command).toHaveBeenCalledWith('/makercheckers/mc1', 'approve');
    service.activateTpp('tp1').subscribe();
    expect(api.command).toHaveBeenCalledWith('/openbanking/tpp/tp1', 'activate');
    service.activateSmsCampaign('sc1').subscribe();
    expect(api.command).toHaveBeenCalledWith('/smscampaigns/sc1', 'activate');
    service.disableStandingInstruction('si1').subscribe();
    expect(api.command).toHaveBeenCalledWith('/standinginstructions/si1', 'disable');
  });

  describe('listAuditLogs routing (entityId alone must NOT trigger /search)', () => {
    it('no filters → /audits', () => {
      service.listAuditLogs(0, {}).subscribe();
      expect(api.getPage).toHaveBeenCalledWith('/audits', 0, 20, { sort: 'changedAt,desc' });
    });

    it('entityType filter → /audits/search', () => {
      service.listAuditLogs(0, { entityType: 'LOAN' }).subscribe();
      expect(api.getPage).toHaveBeenCalledWith('/audits/search', 0, 20, {
        sort: 'changedAt,desc',
        entityType: 'LOAN',
      });
    });

    it('entityId only → /audits (NOT /search), but param still sent', () => {
      service.listAuditLogs(0, { entityId: 'x1' }).subscribe();
      expect(api.getPage).toHaveBeenCalledWith('/audits', 0, 20, {
        sort: 'changedAt,desc',
        entityId: 'x1',
      });
    });
  });

  it('notification templates filter active only when requested', () => {
    service.listNotificationTemplates(true).subscribe();
    expect(api.get).toHaveBeenCalledWith('/notifications/templates', { active: 'true' });
    service.listNotificationTemplates().subscribe();
    expect(api.get).toHaveBeenCalledWith('/notifications/templates', undefined);
  });

  it('login event summary + compliance reports pass the days window', () => {
    service.loginEventSummary(7).subscribe();
    expect(api.get).toHaveBeenCalledWith('/auth/events/summary', { days: 7 });
    service.complianceDataAccess(14, 'CUSTOMER').subscribe();
    expect(api.get).toHaveBeenCalledWith('/compliance/reports/data-access', { days: 14, entityType: 'CUSTOMER' });
  });

  it('bulk import posts multipart FormData', () => {
    const file = new File(['a,b'], 'customers.csv', { type: 'text/csv' });
    service.importCustomers(file).subscribe();
    const [path, fd] = api.postForm.mock.calls[0];
    expect(path).toBe('/bulkimport/customers');
    expect(fd).toBeInstanceOf(FormData);
  });

  it('fraud alerts/cases/blacklist paging passes filters', () => {
    service.listFraudAlerts('OPEN', 'HIGH', 1, 10).subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/fraud/alerts', 1, 10, { status: 'OPEN', severity: 'HIGH' });
    service.listBlacklist('CUSTOMER', false).subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/fraud/blacklist', 0, 20, { entityType: 'CUSTOMER', active: 'false' });
    service.searchBlacklist('john').subscribe();
    expect(api.get).toHaveBeenCalledWith('/fraud/blacklist/search', { q: 'john' });
  });

  it('fraud alert review/close + link-to-case post to sub-paths', () => {
    service.reviewFraudAlert('a1', 'u1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/fraud/alerts/a1/review', { reviewedBy: 'u1' });
    service.closeFraudAlert('a1', 'CONFIRMED', 'u1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/fraud/alerts/a1/close', { status: 'CONFIRMED', reviewedBy: 'u1' });
    service.linkAlertToCase('a1', 'c1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/fraud/cases/c1/alerts/a1', {});
  });

  it('staff list filters by office only when given', () => {
    service.listStaff('o1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/staff', { officeId: 'o1' });
    service.listStaff().subscribe();
    expect(api.get).toHaveBeenCalledWith('/staff', undefined);
  });
});
