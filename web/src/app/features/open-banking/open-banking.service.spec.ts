import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { OpenBankingService } from './open-banking.service';

describe('OpenBankingService', () => {
  let service: OpenBankingService;
  let api: Record<'get' | 'getPage' | 'post', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of({})),
      getPage: vi.fn().mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 })),
      post: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({
      providers: [OpenBankingService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(OpenBankingService);
  });

  it('listConsents pages the staff endpoint with only the filters that are set', () => {
    service.listConsents({ type: 'AISP', status: 'AUTHORISED', tppClientId: 'org-1' }, 2, 20).subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/open-banking/consents', 2, 20, {
      type: 'AISP',
      status: 'AUTHORISED',
      tppClientId: 'org-1',
    });
  });

  it('listConsents with no filters sends no filter params', () => {
    service.listConsents().subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/open-banking/consents', 0, 20, {});
  });

  it('getConsent reads one consent by its consentId', () => {
    service.getConsent('ob-123').subscribe();
    expect(api.get).toHaveBeenCalledWith('/open-banking/consents/ob-123');
  });

  it('revokeConsent posts the reason to the staff revoke endpoint', () => {
    service.revokeConsent('ob-123', 'customer asked').subscribe();
    expect(api.post).toHaveBeenCalledWith('/open-banking/consents/ob-123/revoke', { reason: 'customer asked' });
  });
});
