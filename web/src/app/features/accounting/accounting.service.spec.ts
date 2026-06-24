import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { AccountingService } from './accounting.service';

describe('AccountingService', () => {
  let service: AccountingService;
  let api: Record<'get' | 'getPage' | 'post' | 'put' | 'delete' | 'command' | 'postParams', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of([])),
      getPage: vi.fn().mockReturnValue(of({ content: [] })),
      post: vi.fn().mockReturnValue(of({})),
      put: vi.fn().mockReturnValue(of({})),
      delete: vi.fn().mockReturnValue(of({})),
      command: vi.fn().mockReturnValue(of({})),
      postParams: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({
      providers: [AccountingService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(AccountingService);
  });

  it('financial activity accounts CRUD routes correctly', () => {
    service.listFinancialActivityAccounts().subscribe();
    expect(api.get).toHaveBeenCalledWith('/financialactivityaccounts');
    const req = { financialActivity: 'INCOME_INTEREST' as const, glAccountId: 'gl1' };
    service.createFinancialActivityAccount(req).subscribe();
    expect(api.post).toHaveBeenCalledWith('/financialactivityaccounts', req);
    service.deleteFinancialActivityAccount('fa1').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/financialactivityaccounts/fa1');
  });

  it('GL account enable/disable use the command pattern', () => {
    service.disableGlAccount('gl1').subscribe();
    expect(api.command).toHaveBeenCalledWith('/glaccounts/gl1', 'disable');
    service.enableGlAccount('gl1').subscribe();
    expect(api.command).toHaveBeenCalledWith('/glaccounts/gl1', 'enable');
  });

  it('listGlAccounts forwards optional filter params', () => {
    service.listGlAccounts({ type: 'ASSET' }).subscribe();
    expect(api.get).toHaveBeenCalledWith('/glaccounts', { type: 'ASSET' });
  });

  it('journal entries page at size 50 and reverse posts an empty body', () => {
    service.listJournalEntries({ glAccountId: 'gl1' }).subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/journalentries', 0, 50, { glAccountId: 'gl1' });
    service.reverseJournalEntry('je1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/journalentries/je1/reverse', {});
  });

  describe('GL closures', () => {
    it('listClosures filters by officeId', () => {
      service.listClosures('o1').subscribe();
      expect(api.get).toHaveBeenCalledWith('/glclosures', { officeId: 'o1' });
    });

    it('createClosure posts params and includes comments only when present', () => {
      service.createClosure({ officeId: 'o1', closingDate: '2026-06-01' }).subscribe();
      expect(api.postParams).toHaveBeenCalledWith('/glclosures', {
        officeId: 'o1',
        closingDate: '2026-06-01',
      });

      service.createClosure({ officeId: 'o1', closingDate: '2026-06-01', comments: 'EOY' }).subscribe();
      expect(api.postParams).toHaveBeenCalledWith('/glclosures', {
        officeId: 'o1',
        closingDate: '2026-06-01',
        comments: 'EOY',
      });
    });
  });

  it('getTrialBalance passes the date range', () => {
    service.getTrialBalance('2026-01-01', '2026-06-01').subscribe();
    expect(api.get).toHaveBeenCalledWith('/accounting/trial-balance', {
      fromDate: '2026-01-01',
      toDate: '2026-06-01',
    });
  });

  it('accounting rules page and CRUD route correctly', () => {
    service.listAccountingRules(2).subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/accountingrules', 2, 20);
    service.deleteAccountingRule('ar1').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/accountingrules/ar1');
  });

  it('provisioning criteria CRUD routes correctly', () => {
    service.listProvisioningCriteria().subscribe();
    expect(api.get).toHaveBeenCalledWith('/provisioningcriteria');
    const req = { criteriaName: 'IFRS9', definitions: [] };
    service.createProvisioningCriteria(req).subscribe();
    expect(api.post).toHaveBeenCalledWith('/provisioningcriteria', req);
  });
});
