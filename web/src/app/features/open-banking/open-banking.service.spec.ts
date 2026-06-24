import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { OpenBankingService } from './open-banking.service';

describe('OpenBankingService', () => {
  let service: OpenBankingService;
  let api: Record<'get' | 'command', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of([])),
      command: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({
      providers: [OpenBankingService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(OpenBankingService);
  });

  it('listConsents passes type + status filters when given', () => {
    service.listConsents('AISP', 'AUTHORISED').subscribe();
    expect(api.get).toHaveBeenCalledWith('/open-banking/v3.1/consents', {
      type: 'AISP',
      status: 'AUTHORISED',
    });
  });

  it('listConsents passes undefined params when no filters are given', () => {
    service.listConsents().subscribe();
    expect(api.get).toHaveBeenCalledWith('/open-banking/v3.1/consents', undefined);
  });

  it('getConsent reads a single consent', () => {
    service.getConsent('cn1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/open-banking/v3.1/consents/cn1');
  });

  it('authorise / revoke use the command pattern', () => {
    service.authoriseConsent('cn1').subscribe();
    expect(api.command).toHaveBeenCalledWith('/open-banking/v3.1/consents/cn1', 'authorise');
    service.revokeConsent('cn1').subscribe();
    expect(api.command).toHaveBeenCalledWith('/open-banking/v3.1/consents/cn1', 'revoke');
  });
});
